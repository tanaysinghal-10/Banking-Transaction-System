# 🏦 Banking Transaction System

A **production-grade banking backend** built with Java 17, Spring Boot, and PostgreSQL. Demonstrates ACID compliance, optimistic locking, idempotency, and clean architecture — designed for learning backend engineering and SDE-2 interview preparation.

---

## 📑 Table of Contents

- [Architecture](#-architecture)
- [Setup Guide](#-setup-guide)
- [Database Design](#-database-design)
- [API Reference](#-api-reference)
- [Key Concepts Explained](#-key-concepts-explained)
- [Transaction Flow](#-transaction-flow-transfer)
- [Idempotency Flow](#-idempotency-flow)
- [Failure Scenarios](#-failure-scenarios)
- [Testing](#-testing)
- [Project Structure](#-project-structure)
- [Interview Questions](#-interview-questions)

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT (curl / Postman)                      │
│                    Idempotency-Key: <UUID> header                    │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       CONTROLLER LAYER                               │
│                                                                      │
│  AccountController              TransactionController                │
│  POST /api/v1/accounts          POST /api/v1/transactions/deposit    │
│  GET  /api/v1/accounts/{id}     POST /api/v1/transactions/withdraw   │
│                                 POST /api/v1/transactions/transfer   │
│                                 GET  /api/v1/transactions/account/{id}│
├──────────────────────────────────────────────────────────────────────┤
│                        SERVICE LAYER                                 │
│                                                                      │
│  AccountService          RetryableTransactionService                 │
│  - createAccount()       - deposit() ─── @Retryable + Idempotency   │
│  - getAccountById()      - withdraw()    │                           │
│  - getAccountByNumber()  - transfer()    ▼                           │
│                          TransactionService                          │
│                          - deposit()     IdempotencyService           │
│                          - withdraw()    - findExistingRecord()      │
│                          - transfer()    - saveRecord()              │
│                          - getHistory()  - cleanupExpired()          │
├──────────────────────────────────────────────────────────────────────┤
│                      REPOSITORY LAYER (JPA)                          │
│                                                                      │
│  AccountRepository    TransactionRepository    IdempotencyKeyRepo    │
├──────────────────────────────────────────────────────────────────────┤
│                      PostgreSQL DATABASE                             │
│                                                                      │
│  accounts (with @Version)  │  transactions  │  idempotency_keys      │
└──────────────────────────────────────────────────────────────────────┘
```

### Request Flow

1. **Client** sends HTTP request with `Idempotency-Key` header
2. **Controller** validates the request body (`@Valid`) and delegates to the service layer
3. **RetryableTransactionService** checks idempotency → delegates to `TransactionService`
4. **TransactionService** performs business logic within a `@Transactional` boundary
5. **Repository** executes SQL via Hibernate (with optimistic lock check)
6. If `OptimisticLockException` → Spring Retry re-executes the method
7. Response is cached with the idempotency key for future duplicate detection

---

## 🚀 Setup Guide

### Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | 20+ | `docker --version` |
| Docker Compose | 2.0+ | `docker compose version` |

### Step 1: Start PostgreSQL

```bash
docker compose up -d
```

This starts a PostgreSQL 16 instance with:
- **Database**: `banking_db`
- **Username**: `banking_user`
- **Password**: `banking_pass`
- **Port**: `5432`

Verify it's running:
```bash
docker compose ps
```

### Step 2: Build the Application

```bash
mvn clean compile
```

### Step 3: Run the Application

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

On first startup, the schema is created automatically and 3 sample accounts are seeded:

| Account Number | Holder | Balance |
|---------------|--------|---------|
| ACC-100001 | Alice Johnson | $5,000.00 |
| ACC-100002 | Bob Smith | $3,000.00 |
| ACC-100003 | Charlie Brown | $1,500.00 |

### Step 4: Run Tests

```bash
mvn test
```

---

## 💾 Database Design

### Entity-Relationship Diagram

```
┌─────────────────────┐       ┌──────────────────────────┐
│     accounts         │       │      transactions         │
├─────────────────────┤       ├──────────────────────────┤
│ id (PK, UUID)       │◄──┐   │ id (PK, UUID)            │
│ account_number (UQ)  │   ├───│ source_account_id (FK)   │
│ holder_name          │   ├───│ target_account_id (FK)   │
│ balance (≥0)         │   │   │ type                     │
│ currency             │   │   │ amount (>0)              │
│ status               │   │   │ status                   │
│ version (OPT LOCK)   │   │   │ idempotency_key (UQ)     │
│ created_at           │   │   │ description              │
│ updated_at           │   │   │ created_at               │
└─────────────────────┘   │   └──────────────────────────┘
                           │
                           │   ┌──────────────────────────┐
                           │   │   idempotency_keys        │
                           │   ├──────────────────────────┤
                           │   │ id (PK, UUID)            │
                           │   │ idempotency_key (UQ)     │
                           │   │ request_hash (SHA-256)   │
                           │   │ response_body (TEXT)     │
                           │   │ status_code              │
                           │   │ created_at               │
                           │   │ expires_at               │
                           │   └──────────────────────────┘
```

### Indexing Strategy

| Index | Table | Column(s) | Purpose |
|-------|-------|-----------|---------|
| `idx_accounts_account_number` | accounts | account_number | Fast lookup by account number |
| `idx_accounts_status` | accounts | status | Filter accounts by status |
| `idx_transactions_source_account_id` | transactions | source_account_id | Transaction history query |
| `idx_transactions_target_account_id` | transactions | target_account_id | Transaction history query |
| `idx_transactions_idempotency_key` | transactions | idempotency_key | Duplicate detection |
| `idx_transactions_created_at` | transactions | created_at DESC | Sorted history pagination |
| `idx_idempotency_keys_key` | idempotency_keys | idempotency_key | Idempotency lookup |
| `idx_idempotency_keys_expires_at` | idempotency_keys | expires_at | Cleanup expired records |

### Key Constraints

- **`CHECK (balance >= 0)`**: Database-level protection against negative balances
- **`CHECK (amount > 0)`**: All transaction amounts must be positive
- **`UNIQUE (account_number)`**: No duplicate account numbers
- **`UNIQUE (idempotency_key)`**: Prevents duplicate transaction processing
- **`version` column**: Used by Hibernate for optimistic locking

---

## 📡 API Reference

### Account Endpoints

#### Create Account
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "holderName": "John Doe",
    "initialDeposit": 1000.00,
    "currency": "USD"
  }'
```

Response (201 Created):
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "id": "d4e5f6a7-b8c9-0123-def0-456789abcdef",
    "accountNumber": "ACC-234567",
    "holderName": "John Doe",
    "balance": 1000.00,
    "currency": "USD",
    "status": "ACTIVE",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

#### Get Account by ID
```bash
curl http://localhost:8080/api/v1/accounts/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

#### Get Account by Number
```bash
curl http://localhost:8080/api/v1/accounts/number/ACC-100001
```

### Transaction Endpoints

#### Deposit Money
```bash
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "amount": 500.00,
    "description": "Salary deposit"
  }'
```

#### Withdraw Money
```bash
curl -X POST http://localhost:8080/api/v1/transactions/withdraw \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "amount": 200.00,
    "description": "ATM withdrawal"
  }'
```

#### Transfer Money
```bash
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "sourceAccountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "targetAccountId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "amount": 1000.00,
    "description": "Rent payment"
  }'
```

#### Get Transaction History (Paginated)
```bash
curl "http://localhost:8080/api/v1/transactions/account/a1b2c3d4-e5f6-7890-abcd-ef1234567890?page=0&size=10"
```

### Testing Idempotency

Send the same request with the same `Idempotency-Key` twice:

```bash
# First request — processes normally
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-123" \
  -d '{"accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "amount": 100.00}'

# Second request (same key) — returns cached response, no duplicate deposit
curl -X POST http://localhost:8080/api/v1/transactions/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-123" \
  -d '{"accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "amount": 100.00}'
```

Both return the same response. The account is only credited once.

---

## 📖 Key Concepts Explained

### 1. ACID Properties

ACID is a set of guarantees that database transactions provide:

| Property | What It Means | Example |
|----------|--------------|---------|
| **Atomicity** | All operations in a transaction succeed or all fail. No partial states. | In a transfer, if crediting the target fails, the debit is also rolled back. |
| **Consistency** | The database moves from one valid state to another. | Balance can never go negative (enforced by CHECK constraint). |
| **Isolation** | Concurrent transactions don't interfere with each other. | Two simultaneous withdrawals see consistent balances. |
| **Durability** | Once committed, data survives crashes. | After a successful transfer, the new balances persist even if the server crashes. |

**In this project**: The `@Transactional` annotation ensures atomicity. The `@Version` field ensures isolation. PostgreSQL provides durability.

### 2. Optimistic Locking

**The Problem**: Two users read the same account balance ($1000). Both try to withdraw $600. Without protection, both succeed, resulting in -$200.

**The Solution**: Optimistic locking adds a `version` counter to each row.

```
Thread A: SELECT balance=1000, version=5
Thread B: SELECT balance=1000, version=5

Thread A: UPDATE SET balance=400 WHERE id=? AND version=5  → SUCCESS (version → 6)
Thread B: UPDATE SET balance=400 WHERE id=? AND version=5  → FAILS (version is now 6)
Thread B: RETRIES → reads balance=400, version=6 → insufficient balance → ERROR
```

**Why "Optimistic"?** It assumes conflicts are rare. No locks are held during the read phase. If a conflict does occur, we detect it and retry. This performs better than pessimistic locking when contention is low.

### 3. Idempotency

**The Problem**: You click "Pay $100" and your internet drops. Did it go through? You click again. Without idempotency, you pay $200.

**The Solution**: Each request gets a unique `Idempotency-Key`. The server remembers keys it has already processed.

```
Request 1: Key=abc123, Amount=$100 → PROCESSED, saved to DB
Request 2: Key=abc123, Amount=$100 → KEY EXISTS, return cached response (no new transaction)
```

**Implementation in this project**:
1. Client sends `Idempotency-Key` header with every mutation request
2. Server hashes the request body (SHA-256) and stores it with the key
3. On duplicate: if the hash matches → return cached response
4. If the hash differs → reject (client reusing key with different data is a bug)
5. Keys expire after 24 hours

### 4. Database Transactions

A **transaction** is a group of SQL operations that execute as a single unit.

```java
@Transactional
public void transfer(Account source, Account target, BigDecimal amount) {
    source.setBalance(source.getBalance().subtract(amount));  // SQL UPDATE #1
    target.setBalance(target.getBalance().add(amount));        // SQL UPDATE #2
    // If #2 fails → #1 is automatically rolled back
}
```

Without `@Transactional`, if the server crashes between #1 and #2, money vanishes from the source but never reaches the target.

---

## 🔄 Transaction Flow (Transfer)

Step-by-step walkthrough of `POST /api/v1/transactions/transfer`:

```
Step 1: Client sends request with Idempotency-Key header
        ↓
Step 2: Controller validates request body (@Valid)
        ↓
Step 3: RetryableTransactionService.transfer() called
        ↓
Step 4: Check idempotency key in DB
        ├── Key exists + same hash → Return cached response (DONE)
        ├── Key exists + diff hash → Throw DuplicateRequestException
        └── Key not found → Continue processing
        ↓
Step 5: TransactionService.transfer() called
        Within @Transactional boundary:
        ↓
Step 6: Sort account IDs (deadlock prevention)
        ↓
Step 7: Load both accounts from DB
        (Hibernate reads the version field)
        ↓
Step 8: Validate both accounts are ACTIVE
        ↓
Step 9: Check source has sufficient balance
        ↓
Step 10: Debit source: balance -= amount
Step 11: Credit target: balance += amount
         ↓
Step 12: Save both accounts
         Hibernate generates:
         UPDATE accounts SET balance=?, version=version+1
           WHERE id=? AND version=?
         ↓
         ├── Both UPDATEs affect 1 row → COMMIT
         │   ↓
         │   Step 13: Save Transaction record
         │   Step 14: Cache response with idempotency key
         │   Step 15: Return response to client
         │
         └── UPDATE affects 0 rows → OptimisticLockException
             ↓
             @Transactional rolls back ALL changes
             ↓
             Spring Retry re-executes from Step 4
             (up to 3 attempts with exponential backoff)
```

---

## 🔑 Idempotency Flow

```
                     Client Request
                          │
                 Idempotency-Key: "abc-123"
                 Body: {"amount": 500}
                          │
                          ▼
              ┌───────────────────────┐
              │  Check idempotency DB  │
              │  for key "abc-123"     │
              └───────────┬───────────┘
                          │
                   ┌──────┴──────┐
                   │             │
                Found?        Not Found
                   │             │
                   ▼             ▼
           ┌──────────────┐  ┌──────────────┐
           │ Hash request  │  │ Process the   │
           │ body (SHA-256)│  │ transaction   │
           └──────┬───────┘  │ normally      │
                  │          └──────┬───────┘
           ┌──────┴──────┐         │
           │             │         ▼
       Hash Match?    Hash Mismatch  ┌──────────────┐
           │             │           │ Save response │
           ▼             ▼           │ + hash +      │
    ┌────────────┐ ┌──────────┐     │ idempotency   │
    │ Return     │ │ 409      │     │ key to DB     │
    │ cached     │ │ Conflict │     └──────┬───────┘
    │ response   │ │ Error    │            │
    └────────────┘ └──────────┘            ▼
                                    ┌────────────┐
                                    │ Return     │
                                    │ response   │
                                    └────────────┘
```

---

## 🔥 Failure Scenarios

### 1. Concurrent Withdrawals

**Scenario**: Two ATMs process $500 withdrawals from the same account ($1000 balance) simultaneously.

**Without protection**: Both read $1000, both write $500. Final balance: $500 (should be $0).

**With optimistic locking**:
1. ATM-A reads balance=$1000, version=0
2. ATM-B reads balance=$1000, version=0
3. ATM-A: `UPDATE WHERE version=0` → Success, version=1, balance=$500
4. ATM-B: `UPDATE WHERE version=0` → **Fails** (version is now 1)
5. ATM-B retries: reads balance=$500, version=1 → Success, version=2, balance=$0
6. Final balance: $0 ✅

### 2. Duplicate Transfer Requests

**Scenario**: Client sends a $500 transfer, network drops, client retries with the same Idempotency-Key.

**Flow**:
1. First request: processed, response cached with key
2. Second request: key found in DB, hash matches → return cached response
3. Account debited only once ✅

### 3. Partial Failure During Transfer

**Scenario**: After debiting the source but before crediting the target, the database connection drops.

**Flow**:
1. `@Transactional` begins
2. Source debited: balance = $1000 → $500
3. **Connection drops** before target credit
4. PostgreSQL auto-rolls back the uncommitted transaction
5. Source balance remains at $1000 ✅
6. No money was lost or created

---

## 🧪 Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TransactionServiceTest

# Run the concurrency test specifically
mvn test -Dtest=ConcurrencyTest
```

### Test Coverage

| Test Class | What It Tests |
|-----------|---------------|
| `AccountServiceTest` | Account creation, retrieval, not-found errors |
| `TransactionServiceTest` | Deposit, withdrawal, transfer, insufficient balance, self-transfer |
| `ConcurrencyTest` | 10 concurrent withdrawals on same account, balance consistency |

### Concurrency Test Explained

The `ConcurrencyTest` creates an account with $10,000 and spawns 10 threads, each withdrawing $500 simultaneously. It verifies:

1. **All threads complete** (no deadlocks)
2. **Final balance is exactly $5,000** (no lost updates)
3. **Total success + failure = 10** (every thread accounted for)

---

## 📁 Project Structure

```
src/main/java/com/banking/system/
├── BankingApplication.java          # Spring Boot entry point
├── config/
│   ├── JpaConfig.java               # JPA auditing + scheduling
│   └── RetryConfig.java             # Spring Retry activation
├── controller/
│   ├── AccountController.java       # Account REST endpoints
│   └── TransactionController.java   # Transaction REST endpoints
├── dto/
│   ├── request/
│   │   ├── CreateAccountRequest.java
│   │   ├── DepositRequest.java
│   │   ├── WithdrawRequest.java
│   │   └── TransferRequest.java
│   └── response/
│       ├── AccountResponse.java
│       ├── TransactionResponse.java
│       ├── ApiResponse.java         # Generic wrapper
│       └── ErrorResponse.java       # Error structure
├── exception/
│   ├── AccountInactiveException.java
│   ├── AccountNotFoundException.java
│   ├── DuplicateRequestException.java
│   ├── InsufficientBalanceException.java
│   ├── InvalidTransactionException.java
│   └── GlobalExceptionHandler.java  # Centralized error handling
├── mapper/
│   ├── AccountMapper.java           # Entity ↔ DTO
│   └── TransactionMapper.java
├── model/
│   ├── Account.java                 # @Version for optimistic locking
│   ├── Transaction.java             # Immutable audit record
│   ├── IdempotencyRecord.java       # Cached responses
│   └── enums/
│       ├── AccountStatus.java
│       ├── TransactionStatus.java
│       └── TransactionType.java
├── repository/
│   ├── AccountRepository.java
│   ├── TransactionRepository.java
│   └── IdempotencyKeyRepository.java
└── service/
    ├── AccountService.java          # Account management
    ├── TransactionService.java      # Core financial logic
    ├── RetryableTransactionService.java  # Retry + idempotency wrapper
    └── IdempotencyService.java      # Idempotency management
```

---

## 🎯 Interview Questions

### 1. How do you ensure no money is lost during a transfer?

**Answer**: We use `@Transactional` which wraps the debit and credit in a single database transaction. If either operation fails, the entire transaction is rolled back. Additionally, the database has a `CHECK (balance >= 0)` constraint as defense-in-depth.

### 2. What is optimistic locking and why did you choose it over pessimistic locking?

**Answer**: Optimistic locking uses a version field that's checked on every UPDATE. If the version doesn't match (another transaction modified the row), the update fails and we retry. We chose it because:
- No locks are held during business logic execution (better throughput)
- Banking read operations far outnumber writes
- Conflicts are rare in normal operations
- Pessimistic locking can cause thread starvation under load

### 3. How do you prevent duplicate transactions?

**Answer**: Through idempotency keys. Every mutation request must include a unique `Idempotency-Key` header. Before processing, we check if this key exists in our `idempotency_keys` table. If it does and the request hash matches, we return the cached response. If it doesn't, we process the request and cache the response.

### 4. What happens if two threads try to withdraw from the same account simultaneously?

**Answer**: Both read the current balance. When they try to save:
1. Thread A's UPDATE succeeds (version check passes)
2. Thread B's UPDATE fails (version mismatch → `OptimisticLockException`)
3. Spring Retry catches the exception and re-executes Thread B
4. Thread B re-reads the updated balance and retries
5. If the balance is now insufficient, it throws `InsufficientBalanceException`

### 5. How do you prevent deadlocks in transfers?

**Answer**: When transferring between accounts A and B, we always process them in sorted UUID order. If Thread 1 transfers A→B and Thread 2 transfers B→A, both will lock A first, then B. This consistent ordering prevents circular wait conditions.

### 6. Why use BigDecimal instead of double for money?

**Answer**: Floating-point arithmetic has precision problems. `0.1 + 0.2 = 0.30000000000000004` with double. In the financial domain, a rounding error of $0.01 on millions of transactions compounds into significant losses. BigDecimal provides exact decimal arithmetic.

### 7. What happens if the application crashes mid-transaction?

**Answer**: PostgreSQL automatically rolls back any uncommitted transaction when the connection drops. Since we use `@Transactional`, either all changes commit or none do. No money is created or destroyed.

### 8. Why do you have a separate RetryableTransactionService?

**Answer**: Spring's `@Retryable` works via AOP proxies. Self-invocation (calling a `@Retryable` method from within the same class) bypasses the proxy, so retry doesn't work. By putting retry logic in a separate class, the proxy correctly intercepts calls.

### 9. How does the idempotency key cleanup work?

**Answer**: A `@Scheduled` job runs every hour and deletes records from `idempotency_keys` where `expires_at < NOW()`. Keys expire after 24 hours by default. This prevents unbounded table growth while keeping keys long enough for retry scenarios.

### 10. What's the difference between the `transactions` table and the `idempotency_keys` table?

**Answer**: The `transactions` table is the permanent audit log of all financial operations — it's never deleted. The `idempotency_keys` table is a cache of request/response pairs for duplicate detection — records expire and are cleaned up. They serve different purposes: auditing vs. duplicate prevention.

### 11. How would you scale this system for higher throughput?

**Answer**: 
- **Read replicas**: Route read queries to replicas
- **Connection pooling**: Tune HikariCP pool size
- **Horizontal scaling**: Run multiple app instances behind a load balancer (the database is the single source of truth)
- **Caching**: Add Redis for account lookups (read-heavy)
- **Partitioning**: Partition the transactions table by date for faster queries

### 12. Why is the version field a `Long` and not an `Integer`?

**Answer**: A `Long` gives us 2^63 possible versions (about 9 quintillion). For an account that's updated 1000 times per second, it would take ~292 million years to overflow. An `Integer` would overflow after ~25 days at the same rate.

### 13. What HTTP status codes do you use and why?

**Answer**:
- **201 Created**: Successful creation (account, transaction)
- **200 OK**: Successful retrieval
- **400 Bad Request**: Validation errors, missing headers
- **404 Not Found**: Account doesn't exist
- **409 Conflict**: Optimistic lock failure, duplicate request
- **422 Unprocessable Entity**: Insufficient balance
- **500 Internal Server Error**: Unexpected failures

### 14. How do you handle the case where the same idempotency key is used with a different request body?

**Answer**: We hash the request body using SHA-256 and store it alongside the idempotency key. On a duplicate request, we compare hashes. If they differ, we throw `DuplicateRequestException` (409 Conflict) because reusing a key with different data indicates a client bug.

### 15. What is the role of the `@RestControllerAdvice` in this project?

**Answer**: `GlobalExceptionHandler` with `@RestControllerAdvice` catches all exceptions thrown by any controller and maps them to structured JSON error responses. This ensures:
- Consistent error format across all endpoints
- Proper HTTP status codes for each error type
- Centralized logging of all failures
- Controllers stay clean (no try-catch blocks)
- Internal details (stack traces) are never leaked to clients

---

## 📄 License

This project is for educational purposes. Use it freely for learning and interview preparation.
