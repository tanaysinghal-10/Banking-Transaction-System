# Interview Questions for Banking Transaction System

## 1. How do you ensure no money is lost during a transfer?

**Answer**: We use `@Transactional` which wraps the debit and credit in a single database transaction. If either operation fails, the entire transaction is rolled back. Additionally, the database has a `CHECK (balance >= 0)` constraint as defense-in-depth.

## 2. What is optimistic locking and why did you choose it over pessimistic locking?

**Answer**: Optimistic locking uses a version field that's checked on every UPDATE. If the version doesn't match (another transaction modified the row), the update fails and we retry. We chose it because:
- No locks are held during business logic execution (better throughput)
- Banking read operations far outnumber writes
- Conflicts are rare in normal operations
- Pessimistic locking can cause thread starvation under load

## 3. How do you prevent duplicate transactions?

**Answer**: Through idempotency keys. Every mutation request must include a unique `Idempotency-Key` header. Before processing, we check if this key exists in our `idempotency_keys` table. If it does and the request hash matches, we return the cached response. If it doesn't, we process the request and cache the response.

## 4. What happens if two threads try to withdraw from the same account simultaneously?

**Answer**: Both read the current balance. When they try to save:
1. Thread A's UPDATE succeeds (version check passes)
2. Thread B's UPDATE fails (version mismatch → `OptimisticLockException`)
3. Spring Retry catches the exception and re-executes Thread B
4. Thread B re-reads the updated balance and retries
5. If the balance is now insufficient, it throws `InsufficientBalanceException`

## 5. How do you prevent deadlocks in transfers?

**Answer**: When transferring between accounts A and B, we always process them in sorted UUID order. If Thread 1 transfers A→B and Thread 2 transfers B→A, both will lock A first, then B. This consistent ordering prevents circular wait conditions.

## 6. Why use BigDecimal instead of double for money?

**Answer**: Floating-point arithmetic has precision problems. `0.1 + 0.2 = 0.30000000000000004` with double. In the financial domain, a rounding error of $0.01 on millions of transactions compounds into significant losses. BigDecimal provides exact decimal arithmetic.

## 7. What happens if the application crashes mid-transaction?

**Answer**: PostgreSQL automatically rolls back any uncommitted transaction when the connection drops. Since we use `@Transactional`, either all changes commit or none do. No money is created or destroyed.

## 8. Why do you have a separate RetryableTransactionService?

**Answer**: Spring's `@Retryable` works via AOP proxies. Self-invocation (calling a `@Retryable` method from within the same class) bypasses the proxy, so retry doesn't work. By putting retry logic in a separate class, the proxy correctly intercepts calls.

## 9. How does the idempotency key cleanup work?

**Answer**: A `@Scheduled` job runs every hour and deletes records from `idempotency_keys` where `expires_at < NOW()`. Keys expire after 24 hours by default. This prevents unbounded table growth while keeping keys long enough for retry scenarios.

## 10. What's the difference between the `transactions` table and the `idempotency_keys` table?

**Answer**: The `transactions` table is the permanent audit log of all financial operations — it's never deleted. The `idempotency_keys` table is a cache of request/response pairs for duplicate detection — records expire and are cleaned up. They serve different purposes: auditing vs. duplicate prevention.

## 11. How would you scale this system for higher throughput?

**Answer**: 
- **Read replicas**: Route read queries to replicas
- **Connection pooling**: Tune HikariCP pool size
- **Horizontal scaling**: Run multiple app instances behind a load balancer (the database is the single source of truth)
- **Caching**: Add Redis for account lookups (read-heavy)
- **Partitioning**: Partition the transactions table by date for faster queries

## 12. Why is the version field a `Long` and not an `Integer`?

**Answer**: A `Long` gives us 2^63 possible versions (about 9 quintillion). For an account that's updated 1000 times per second, it would take ~292 million years to overflow. An `Integer` would overflow after ~25 days at the same rate.

## 13. What HTTP status codes do you use and why?

**Answer**:
- **201 Created**: Successful creation (account, transaction)
- **200 OK**: Successful retrieval
- **400 Bad Request**: Validation errors, missing headers
- **404 Not Found**: Account doesn't exist
- **409 Conflict**: Optimistic lock failure, duplicate request
- **422 Unprocessable Entity**: Insufficient balance
- **500 Internal Server Error**: Unexpected failures

## 14. How do you handle the case where the same idempotency key is used with a different request body?

**Answer**: We hash the request body using SHA-256 and store it alongside the idempotency key. On a duplicate request, we compare hashes. If they differ, we throw `DuplicateRequestException` (409 Conflict) because reusing a key with different data indicates a client bug.

## 15. What is the role of the `@RestControllerAdvice` in this project?

**Answer**: `GlobalExceptionHandler` with `@RestControllerAdvice` catches all exceptions thrown by any controller and maps them to structured JSON error responses. This ensures:
- Consistent error format across all endpoints
- Proper HTTP status codes for each error type
- Centralized logging of all failures
- Controllers stay clean (no try-catch blocks)
- Internal details (stack traces) are never leaked to clients
