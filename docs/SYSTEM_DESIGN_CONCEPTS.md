# 📚 System Design Concepts — Complete Guide

## Every Concept Used in the Banking Transaction System, Explained from Scratch

This document explains **every system design concept** used in this project. Each concept includes:
- **What it is** (simple definition)
- **Why it matters** (real-world importance)
- **How it works in this project** (with code references)
- **Real-world analogy** (to make it click)
- **Interview-ready explanation** (concise version for interviews)

---

## 📑 Table of Contents

1. [ACID Properties](#1-acid-properties)
2. [Database Transactions](#2-database-transactions)
3. [Optimistic Locking](#3-optimistic-locking)
4. [Pessimistic Locking (and why we didn't use it)](#4-pessimistic-locking)
5. [Idempotency](#5-idempotency)
6. [Concurrency Control](#6-concurrency-control)
7. [Deadlock Prevention](#7-deadlock-prevention)
8. [Retry Pattern with Exponential Backoff](#8-retry-pattern-with-exponential-backoff)
9. [3-Tier / Layered Architecture](#9-3-tier--layered-architecture)
10. [DTO Pattern (Data Transfer Object)](#10-dto-pattern-data-transfer-object)
11. [Repository Pattern](#11-repository-pattern)
12. [ORM (Object-Relational Mapping)](#12-orm-object-relational-mapping)
13. [Database Indexing](#13-database-indexing)
14. [Database Constraints](#14-database-constraints)
15. [Connection Pooling](#15-connection-pooling)
16. [Exception Handling Strategy](#16-exception-handling-strategy)
17. [API Design (RESTful APIs)](#17-api-design-restful-apis)
18. [Request Validation](#18-request-validation)
19. [Pagination](#19-pagination)
20. [Hashing (SHA-256)](#20-hashing-sha-256)
21. [Defense in Depth](#21-defense-in-depth)
22. [Structured Logging](#22-structured-logging)
23. [AOP (Aspect-Oriented Programming)](#23-aop-aspect-oriented-programming)
24. [Dependency Injection](#24-dependency-injection)
25. [Database Normalization](#25-database-normalization)

---

## 1. ACID Properties

### What It Is

ACID is a set of four guarantees that database systems provide to ensure data reliability. Every serious database (PostgreSQL, MySQL, Oracle) supports ACID. It stands for:

| Letter | Property | One-Line Meaning |
|--------|----------|-----------------|
| **A** | Atomicity | All or nothing — partial operations are impossible |
| **C** | Consistency | Database always moves from one valid state to another |
| **I** | Isolation | Concurrent operations don't interfere with each other |
| **D** | Durability | Once saved, data survives crashes and power outages |

### Why It Matters

Imagine transferring $500 from Account A to Account B. This requires TWO operations:
1. Subtract $500 from A
2. Add $500 to B

Without ACID, what could go wrong?

| Problem | Without ACID | With ACID |
|---------|-------------|-----------|
| Server crashes after step 1 | A loses $500, B never gets it. Money vanishes. | Both steps roll back. A and B unchanged. |
| Two people read A's balance at the same time | Both see $1000 and both withdraw $800. Balance becomes -$600. | One succeeds, the other sees updated balance. |
| Power outage after "save complete" message | Data might be lost from memory. | Data was written to disk before confirming. |

### How It Works in This Project

```java
// In TransactionService.java
@Transactional  // ← THIS ANNOTATION GIVES US ACID
public TransactionResponse transfer(TransferRequest request) {
    // Step 1: Debit source account
    source.setBalance(source.getBalance().subtract(request.getAmount()));
    accountRepository.save(source);

    // Step 2: Credit target account
    target.setBalance(target.getBalance().add(request.getAmount()));
    accountRepository.save(target);

    // Step 3: Record the transaction
    transactionRepository.save(transaction);

    // If ANY of the above fail, ALL three changes are rolled back.
    // The database is NEVER in a state where source is debited
    // but target is not credited.
}
```

**Atomicity**: The `@Transactional` annotation wraps all three saves into a single database transaction. Either all three succeed, or none of them do.

**Consistency**: The database has a `CHECK (balance >= 0)` constraint. Even if our Java code has a bug, the database itself won't allow a negative balance.

**Isolation**: The `@Version` field ensures concurrent transactions see consistent data (explained in Optimistic Locking section).

**Durability**: PostgreSQL writes committed transactions to the Write-Ahead Log (WAL) on disk. Even a power failure can't lose committed data.

### Real-World Analogy

Think of ACID like a bank vault with safety procedures:
- **Atomicity**: When you transfer money, the teller completes BOTH the debit and credit slips before processing. If either slip is wrong, both are torn up.
- **Consistency**: The vault door won't open if the combination is wrong (constraints prevent invalid states).
- **Isolation**: Two tellers working on the same account use a "currently being modified" flag to avoid conflicts.
- **Durability**: Every transaction is written in pen (not pencil) in the ledger book. Even if the building floods, the backup copy survives.

### Interview-Ready Explanation

> "ACID guarantees data reliability in database transactions. In our banking system, when a transfer happens, `@Transactional` ensures the debit and credit either both commit or both roll back (Atomicity). CHECK constraints prevent invalid states like negative balances (Consistency). Optimistic locking via `@Version` handles concurrent access (Isolation). And PostgreSQL's WAL ensures committed data survives crashes (Durability)."

---

## 2. Database Transactions

### What It Is

A database transaction is a sequence of SQL operations that are treated as **a single unit of work**. The database either executes ALL of them or NONE of them.

### Why It Matters

Without transactions, you're sending individual SQL statements to the database, and any failure between them leaves your data in a broken state.

### How It Works

```
Without Transaction:
───────────────────
UPDATE accounts SET balance = balance - 500 WHERE id = 'alice';
-- ❌ CONNECTION DROPS HERE
UPDATE accounts SET balance = balance + 500 WHERE id = 'bob';
-- Alice lost $500, Bob never got it. Money is GONE.

With Transaction:
─────────────────
BEGIN;
  UPDATE accounts SET balance = balance - 500 WHERE id = 'alice';
  -- ❌ CONNECTION DROPS HERE
  UPDATE accounts SET balance = balance + 500 WHERE id = 'bob';
COMMIT;
-- PostgreSQL auto-ROLLBACKs. Alice still has her $500.
```

### How It Works in This Project

Spring Boot's `@Transactional` annotation translates to:

```
1. Spring intercepts the method call
2. Opens a database connection
3. Sends: BEGIN
4. Executes your Java code (all JPA saves go through this one connection)
5. If no exception: sends COMMIT
6. If exception thrown: sends ROLLBACK
7. Closes/returns the connection to the pool
```

```java
@Transactional  // Spring wraps this method in BEGIN...COMMIT/ROLLBACK
public TransactionResponse transfer(TransferRequest request) {
    // All JPA operations here share the same database transaction
}
```

### Important Detail: `readOnly = true`

```java
@Transactional(readOnly = true)  // Optimization for read-only methods
public AccountResponse getAccountById(UUID id) { ... }
```

When you mark a transaction as read-only:
- Hibernate skips "dirty checking" (doesn't look for modified entities to flush)
- The JDBC driver may route to a read replica
- It's a **performance optimization**, not a strict enforcement

### Real-World Analogy

Think of a transaction like writing a cheque:
- You write the amount, sign it, and hand it to the teller.
- The teller processes it as one complete action.
- If the cheque bounces (insufficient funds), nothing happens — your account isn't debited, the recipient doesn't get credited.
- There's no state where the cheque is "half-processed."

### Interview-Ready Explanation

> "A database transaction groups multiple SQL operations into an atomic unit. In Spring Boot, we use `@Transactional` which wraps the method in BEGIN/COMMIT. If any exception occurs, all changes are automatically rolled back. For transfers, this ensures the debit and credit always happen together or not at all."

---

## 3. Optimistic Locking

### What It Is

Optimistic Locking is a **concurrency control strategy** that assumes conflicts between users are **rare**. Instead of locking a database row when someone reads it (which blocks others), it lets everyone read freely and checks for conflicts **only when saving**.

### The Problem Without It (Lost Update)

```
Timeline:
─────────────────────────────────────────────────
Time 1: User A reads account balance: $1000
Time 2: User B reads account balance: $1000
Time 3: User A withdraws $600.  Saves balance = $1000 - $600 = $400 ✅
Time 4: User B withdraws $600.  Saves balance = $1000 - $600 = $400 ✅

Final balance: $400 (should be -$200 or one should have failed!)
Both withdrawals succeeded, but the account only had $1000.
User A's withdrawal was LOST — that's the "lost update" problem.
```

### How Optimistic Locking Solves It

Add a `version` column to every row. Every UPDATE must check and increment the version:

```
Time 1: User A reads balance=$1000, version=0
Time 2: User B reads balance=$1000, version=0
Time 3: User A saves:
         UPDATE SET balance=400, version=1 WHERE id=X AND version=0
         → Affects 1 row ✅ (version was 0, now it's 1)
Time 4: User B saves:
         UPDATE SET balance=400, version=1 WHERE id=X AND version=0
         → Affects 0 rows ❌ (version is now 1, not 0)
         → Hibernate throws OptimisticLockException!
         → User B must RETRY with the new balance ($400)
         → $400 < $600 → Insufficient balance → REJECTED correctly
```

### How It Works in This Project

**Step 1: The `@Version` annotation on the entity**

```java
// Account.java
@Entity
public class Account {
    @Version  // ← Tells Hibernate to use optimistic locking
    private Long version;
    // ...
}
```

**Step 2: Hibernate auto-generates version-checking SQL**

When you call `accountRepository.save(account)`, Hibernate generates:

```sql
UPDATE accounts
SET balance = ?, holder_name = ?, version = version + 1
WHERE id = ? AND version = ?
--                        ^^^^^^^^^
--                    This is the magic part!
```

If the WHERE clause matches 0 rows (version changed), Hibernate throws `ObjectOptimisticLockingFailureException`.

**Step 3: Spring Retry catches the exception and retries**

```java
// RetryableTransactionService.java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2.0)
)
public TransactionResponse withdraw(WithdrawRequest request, String key) {
    // If OptimisticLockException occurs, this whole method re-runs
    // with FRESH data from the database
}
```

### Optimistic vs Pessimistic: When to Use Which?

| Factor | Optimistic Locking | Pessimistic Locking |
|--------|-------------------|-------------------|
| **Assumption** | Conflicts are rare | Conflicts are common |
| **How it works** | Check at write time | Lock at read time |
| **Performance** | Better for read-heavy workloads | Better for write-heavy workloads |
| **Throughput** | Higher (no waiting) | Lower (threads block) |
| **Failures** | Need retry logic | No retry needed |
| **Deadlocks** | Impossible | Possible |
| **Best for** | Web apps, banking reads | Inventory systems with limited stock |

### Real-World Analogy

**Optimistic Locking** is like editing a Google Doc:
- Everyone can open and edit the document.
- When you try to save, Google checks if someone else edited the same paragraph.
- If yes → "This paragraph was changed. Please review." (conflict detected, you retry)
- If no → Your changes are saved.

**Pessimistic Locking** is like checking out a library book:
- Only one person can have the book at a time.
- Everyone else waits until it's returned.
- No conflicts possible, but lots of waiting.

### Interview-Ready Explanation

> "We use optimistic locking via JPA's `@Version` annotation. Every account has a version field. When Hibernate generates an UPDATE, it includes `WHERE version = ?`. If the row was modified by another transaction (version changed), the update affects 0 rows and throws OptimisticLockException. Spring Retry then re-executes the operation with fresh data. We chose optimistic over pessimistic locking because banking reads far outnumber writes, so holding locks would hurt throughput."

---

## 4. Pessimistic Locking

### What It Is

Pessimistic Locking locks the database row at **read time**, preventing anyone else from reading or modifying it until the lock is released (usually when the transaction commits).

### How It Works

```sql
-- Pessimistic locking SQL
SELECT * FROM accounts WHERE id = 'X' FOR UPDATE;
-- This row is now LOCKED. No other transaction can modify it.

UPDATE accounts SET balance = 400 WHERE id = 'X';
-- Update succeeds because we hold the lock.

COMMIT;
-- Lock is released.
```

### Why We Didn't Use It in This Project

1. **Lower throughput**: While one transaction holds the lock, all other transactions that want the same account must WAIT. In a banking app where thousands of users check their balance (reads), pessimistic locking would create massive bottlenecks.

2. **Deadlock risk**: If Transaction A locks Account 1 and waits for Account 2, while Transaction B locks Account 2 and waits for Account 1 → **deadlock** (both wait forever).

3. **Connection exhaustion**: Locked transactions hold database connections longer, which can exhaust the connection pool under load.

### When Pessimistic Locking IS Better

- **Inventory/ticketing systems**: Only 5 concert tickets left, 100 people trying to buy. You NEED to lock the row to prevent overselling.
- **High-contention scenarios**: When many threads frequently modify the SAME row, optimistic locking would cause too many retries.

### Interview-Ready Explanation

> "We chose optimistic locking over pessimistic because our banking system is read-heavy. Pessimistic locking holds database locks during the entire transaction, reducing throughput. It also introduces deadlock risk when multiple transactions lock the same rows in different orders. Optimistic locking lets us detect conflicts cheaply at commit time and retry, which performs better under low-to-moderate contention."

---

## 5. Idempotency

### What It Is

An operation is **idempotent** if performing it multiple times has the **same effect as performing it once**.

| Operation | Idempotent? | Why |
|-----------|------------|-----|
| `x = 5` | ✅ Yes | Setting x to 5 ten times still gives x = 5 |
| `x = x + 1` | ❌ No | Doing it 3 times gives x = 8 (not 6) |
| GET /accounts/123 | ✅ Yes | Reading data doesn't change anything |
| POST /deposit (without idempotency key) | ❌ No | Each call adds money again |
| POST /deposit (with idempotency key) | ✅ Yes | Duplicate calls return cached response |

### Why It Matters in Banking

Networks are unreliable. Consider this scenario:

```
1. You click "Send $500 to Bob"
2. Your app sends the request to the server
3. Server processes it (Bob gets $500)
4. Server sends the response back
5. ❌ Your internet drops BEFORE the response reaches you
6. Your app shows "Error: Request timed out"
7. You think it didn't work, so you click "Send $500 to Bob" AGAIN
8. Without idempotency: Bob gets ANOTHER $500 (total $1000) 💸
9. With idempotency: Server returns cached response, Bob still has $500 ✅
```

### How It Works in This Project

**Step 1: Client generates a unique key for each NEW operation**
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

**Step 2: Server checks if this key was already processed**
```java
// RetryableTransactionService.java
Optional<IdempotencyRecord> existing = idempotencyService
    .findExistingRecord(idempotencyKey);

if (existing.isPresent()) {
    // Key found! Check if the request body is the same
    String currentHash = idempotencyService.hashRequest(requestJson);

    if (record.getRequestHash().equals(currentHash)) {
        // Same key + same request = DUPLICATE → return cached response
        return fromJson(record.getResponseBody(), TransactionResponse.class);
    } else {
        // Same key + DIFFERENT request = CLIENT BUG → reject
        throw new DuplicateRequestException("Key already used with different data");
    }
}
```

**Step 3: If new, process and cache the response**
```java
// Process the transaction normally
TransactionResponse response = transactionService.deposit(request);

// Cache the response with the key (expires in 24 hours)
idempotencyService.saveRecord(
    idempotencyKey,      // The unique key
    requestJson,          // SHA-256 hash of request body
    responseJson,         // The full JSON response
    200                  // HTTP status code
);
```

**Step 4: Cleanup**
```java
// IdempotencyService.java — runs every hour
@Scheduled(cron = "0 0 * * * *")
public void cleanupExpiredRecords() {
    // Delete records older than 24 hours
    repository.deleteByExpiresAtBefore(LocalDateTime.now());
}
```

### The Full Flow Diagram

```
           Request arrives with Idempotency-Key: "abc-123"
                              │
                              ▼
                    ┌─────────────────┐
                    │ Look up "abc-123"│
                    │ in database      │
                    └────────┬────────┘
                             │
                   ┌─────────┴─────────┐
                   │                   │
                 Found              Not Found
                   │                   │
                   ▼                   ▼
          ┌─────────────┐    ┌──────────────────┐
          │ Hash request │    │ Process normally  │
          │ body (SHA-256)│    │ (deposit/withdraw/│
          │ & compare    │    │  transfer)        │
          └──────┬──────┘    └────────┬─────────┘
                 │                    │
          ┌──────┴──────┐             ▼
          │             │     ┌──────────────────┐
       Matches      Differs   │ Save response +   │
          │             │     │ key + hash to DB  │
          ▼             ▼     └────────┬─────────┘
   ┌───────────┐ ┌──────────┐         │
   │ Return    │ │ 409 Error│         ▼
   │ cached    │ │ "Key used│   ┌───────────┐
   │ response  │ │ with diff│   │ Return    │
   │ (no new   │ │ data"    │   │ response  │
   │ processing│ └──────────┘   └───────────┘
   │ happens)  │
   └───────────┘
```

### Why 24-Hour Expiry?

- **Storage**: Each record is ~1KB. At 1 million requests/day, that's 1GB/day without cleanup.
- **Relevance**: After 24 hours, if someone sends the same request, it's almost certainly intentional (a NEW action, not a retry).
- **Security**: Old idempotency records serve no purpose and increase attack surface.

### Real-World Analogy

Think of a receipt at a store:
1. You buy a shirt. The cashier gives you a receipt with number #001234.
2. The shirt doesn't arrive by mail. You go back to the store and show receipt #001234.
3. The store looks up #001234 → "Already processed. Here's your tracking number."
4. They do NOT charge you again. They just replay the original response.
5. If you showed receipt #001234 but asked for PANTS instead → "This receipt is for a shirt, not pants!"

### Interview-Ready Explanation

> "We implement idempotency using a database-backed approach. Every mutation request requires an Idempotency-Key header. Before processing, we check if this key exists in our idempotency_keys table. If found and the request hash matches, we return the cached response without reprocessing. If found but the hash differs, we reject it as a client error. If not found, we process normally and cache the response. Records expire after 24 hours via a scheduled cleanup job."

---

## 6. Concurrency Control

### What It Is

Concurrency control is the set of techniques used to **safely handle multiple users accessing the same data at the same time**. Without it, simultaneous operations corrupt data.

### The Two Main Approaches

| Approach | Strategy | Analogy |
|----------|----------|---------|
| **Optimistic** | Let everyone work, check for conflicts at the end | Google Docs — edit freely, resolve conflicts when saving |
| **Pessimistic** | Lock data before working, release when done | Library books — one person at a time |

### How This Project Uses It

We use **three layers** of concurrency control:

**Layer 1: Optimistic Locking (`@Version`)**
```java
// Every account save checks the version
UPDATE accounts SET balance=?, version=version+1
WHERE id=? AND version=?
```

**Layer 2: Spring Retry**
```java
// Automatic retry when optimistic locking fails
@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
```

**Layer 3: Database Constraints**
```sql
-- Even if all else fails, the database enforces this
CHECK (balance >= 0)
```

### Concurrency Test in the Project

Our `ConcurrencyTest.java` proves this works:

```java
// Create account with $10,000
// Launch 10 threads, each withdrawing $500
// Expected result: $5,000 (10 * $500 withdrawn)
// Actual result: $5,000 ✅ (no lost updates!)
```

What happens internally during the test:

```
Thread 1: READ  balance=$10000, version=0
Thread 2: READ  balance=$10000, version=0
Thread 3: READ  balance=$10000, version=0
...
Thread 1: WRITE balance=$9500 WHERE version=0 → ✅ version=1
Thread 2: WRITE balance=$9500 WHERE version=0 → ❌ (version=1 now)
Thread 2: RETRY → READ balance=$9500, version=1
Thread 2: WRITE balance=$9000 WHERE version=1 → ✅ version=2
Thread 3: WRITE balance=$9500 WHERE version=0 → ❌ (version=2 now)
Thread 3: RETRY → READ balance=$9000, version=2
... and so on until all threads complete
Final balance: $5000 ✅
```

### Interview-Ready Explanation

> "We handle concurrency through optimistic locking with `@Version`, backed by Spring Retry for automatic retries on conflicts. The concurrency test proves this works by spawning 10 threads that simultaneously withdraw from the same account, verifying the final balance is exactly correct with no lost updates."

---

## 7. Deadlock Prevention

### What It Is

A **deadlock** occurs when two or more transactions are each waiting for the other to release a resource. Neither can continue. The system is stuck.

### The Classic Deadlock Scenario

```
Thread A: Transfer Alice → Bob
  1. Lock Alice's row ✅
  2. Try to lock Bob's row ⏳ (waiting for Thread B...)

Thread B: Transfer Bob → Alice
  1. Lock Bob's row ✅
  2. Try to lock Alice's row ⏳ (waiting for Thread A...)

DEADLOCK! Both threads wait for each other forever.
```

### How We Prevent It: Consistent Ordering

**Rule**: Always process accounts in sorted UUID order, regardless of who is the source and who is the target.

```java
// TransactionService.java — transfer method
UUID firstId, secondId;
if (request.getSourceAccountId().compareTo(request.getTargetAccountId()) < 0) {
    firstId = request.getSourceAccountId();   // Process this account first
    secondId = request.getTargetAccountId();  // Process this account second
} else {
    firstId = request.getTargetAccountId();
    secondId = request.getSourceAccountId();
}

// Always load/lock in this consistent order
Account firstAccount = loadAndValidateAccount(firstId);
Account secondAccount = loadAndValidateAccount(secondId);
```

**Now the deadlock is impossible**:

```
Alice's UUID: "aaaa..."  (sorts first)
Bob's UUID:   "bbbb..."  (sorts second)

Thread A (Alice → Bob): Lock "aaaa" first, then "bbbb"
Thread B (Bob → Alice): Lock "aaaa" first, then "bbbb"  ← SAME ORDER!

Thread A: Lock Alice ✅, Lock Bob ✅ → Success
Thread B: Lock Alice ⏳ (waiting for A to finish, not deadlocked)
Thread A: Commits, releases locks
Thread B: Lock Alice ✅, Lock Bob ✅ → Success
```

### The Four Conditions for Deadlock

A deadlock requires ALL four conditions (Coffman conditions):

| Condition | What It Means | Do We Have It? |
|-----------|--------------|---------------|
| **Mutual Exclusion** | Only one transaction can hold a resource | Yes |
| **Hold and Wait** | A process holds one lock while waiting for another | Yes |
| **No Preemption** | Locks can't be forcibly taken away | Yes |
| **Circular Wait** | A→B→A circular dependency | **NO!** Consistent ordering prevents this |

By eliminating circular wait, we eliminate deadlocks.

### Real-World Analogy

Imagine two people at a narrow doorway, each trying to go through from opposite sides. They block each other. **Solution**: Put up a sign that says "People going A→Z go first." Now both follow the same rule, no blocking.

### Interview-Ready Explanation

> "We prevent deadlocks in transfers by always locking accounts in sorted UUID order. If Alice transfers to Bob and Bob transfers to Alice concurrently, both threads will lock Alice's account first (lower UUID), then Bob's. This eliminates circular wait, one of the four necessary conditions for deadlock."

---

## 8. Retry Pattern with Exponential Backoff

### What It Is

The retry pattern automatically re-executes an operation after a transient failure. **Exponential backoff** means the wait time between retries increases exponentially, giving the system time to recover.

### Why Not Just Retry Immediately?

If 100 transactions all fail at the same time and all retry immediately, they'll likely all fail AGAIN (stampeding herd). Exponential backoff spreads out the retries:

```
Without backoff:
  Attempt 1: FAIL (wait 0ms)
  Attempt 2: FAIL (wait 0ms)  ← all 100 retries hit at once, same problem
  Attempt 3: FAIL

With exponential backoff:
  Attempt 1: FAIL (wait 100ms)
  Attempt 2: FAIL (wait 200ms)  ← retries spread out over time
  Attempt 3: FAIL (wait 400ms)  ← even more spread out
```

### How It Works in This Project

```java
// RetryableTransactionService.java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,  // Only retry this exception
    maxAttempts = 3,                                            // Try 3 times total
    backoff = @Backoff(
        delay = 100,      // First retry waits 100ms
        multiplier = 2.0,  // Each subsequent wait is 2x longer
        maxDelay = 1000    // Never wait more than 1 second
    )
)
public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
    // This entire method re-runs on OptimisticLockException
}
```

**Retry timeline**:
```
Attempt 1: Execute → OptimisticLockException
            ⏱ Wait 100ms
Attempt 2: Execute → OptimisticLockException
            ⏱ Wait 200ms (100 × 2.0)
Attempt 3: Execute → OptimisticLockException
            🚨 All retries exhausted → @Recover method called → Exception thrown to client
```

### What Happens When All Retries Are Exhausted?

```java
@Recover  // Called when maxAttempts is exceeded
public TransactionResponse recoverDeposit(
        ObjectOptimisticLockingFailureException ex,
        DepositRequest request, String idempotencyKey) {
    log.error("All retries exhausted for deposit | Key: {}", idempotencyKey);
    throw ex;  // Re-throw → GlobalExceptionHandler returns 409 to client
}
```

### Why @Retryable Is in a Separate Class

Spring's `@Retryable` works through **AOP proxies** (explained later). A proxy wraps the class and intercepts method calls. But if you call a method within the SAME class, the proxy is bypassed:

```java
// ❌ WRONG — retry won't work
public class TransactionService {
    @Retryable
    public void deposit() { ... }

    public void doSomething() {
        this.deposit();  // Direct call bypasses proxy! Retry doesn't work!
    }
}

// ✅ CORRECT — separate class
public class RetryableTransactionService {
    @Retryable
    public void deposit() {
        transactionService.deposit();  // Goes through proxy → retry works!
    }
}
```

### Real-World Analogy

Imagine calling a busy phone number:
1. First call: busy signal → wait 1 second → try again
2. Second call: still busy → wait 2 seconds → try again
3. Third call: still busy → wait 4 seconds → try again
4. Fourth call: give up, call back tomorrow

### Interview-Ready Explanation

> "We use Spring Retry with exponential backoff to handle OptimisticLockException. On a conflict, the method is retried up to 3 times with delays of 100ms, 200ms, and 400ms. The `@Retryable` annotation is placed on a separate wrapper class because Spring's AOP proxy doesn't intercept self-invocations. If all retries are exhausted, the `@Recover` method re-throws the exception, and the global exception handler returns a 409 Conflict to the client."

---

## 9. 3-Tier / Layered Architecture

### What It Is

A design pattern that separates an application into three distinct layers, each with a specific responsibility:

```
┌──────────────────────────────────────┐
│         CONTROLLER LAYER              │  ← Handles HTTP requests and responses
│   (What the outside world sees)       │
├──────────────────────────────────────┤
│          SERVICE LAYER                │  ← Contains business logic
│   (Where the rules live)             │
├──────────────────────────────────────┤
│         REPOSITORY LAYER              │  ← Talks to the database
│   (Where data is stored/retrieved)   │
└──────────────────────────────────────┘
```

### Why It Matters

| Benefit | Explanation |
|---------|-------------|
| **Separation of concerns** | Each layer does ONE thing well |
| **Testability** | You can test the service layer without a database (mock the repository) |
| **Maintainability** | Change the database from PostgreSQL to MySQL? Only the repository layer changes. |
| **Team scalability** | Different developers can work on different layers without conflicts |

### How It Works in This Project

```
Client sends: POST /api/v1/transactions/deposit

CONTROLLER (TransactionController.java):
  → Validates the request (@Valid)
  → Extracts the Idempotency-Key header
  → Calls the service layer
  → Returns the HTTP response with proper status code
  → Does NOT contain any business logic

SERVICE (TransactionService.java):
  → Checks if account exists
  → Checks if account is active
  → Checks if balance is sufficient (for withdrawals)
  → Updates balances
  → Creates transaction record
  → Does NOT know about HTTP, headers, or JSON

REPOSITORY (AccountRepository.java):
  → Executes SQL: SELECT * FROM accounts WHERE id = ?
  → Executes SQL: UPDATE accounts SET balance = ? WHERE id = ?
  → Does NOT know about business rules
```

### Anti-Pattern: What NOT to Do

```java
// ❌ BAD — Business logic in the controller
@PostMapping("/withdraw")
public ResponseEntity withdraw(@RequestBody WithdrawRequest request) {
    Account account = accountRepository.findById(request.getId());
    if (account.getBalance() < request.getAmount()) {  // Business logic leaked!
        return ResponseEntity.badRequest().body("Insufficient funds");
    }
    account.setBalance(account.getBalance() - request.getAmount());
    accountRepository.save(account);
    return ResponseEntity.ok(account);  // Entity exposed directly!
}
```

### Interview-Ready Explanation

> "We use a 3-tier architecture: Controller, Service, and Repository. Controllers handle HTTP concerns (validation, status codes). Services contain business logic (balance checks, transfer rules). Repositories handle data access. This separation makes the code testable (mock repositories in service tests), maintainable (change DB without touching business logic), and follows the Single Responsibility Principle."

---

## 10. DTO Pattern (Data Transfer Object)

### What It Is

A DTO is a simple object used to **transfer data between layers**. It's separate from the database entity and contains only the fields that the caller needs.

### Why Not Just Use the Entity Directly?

| Problem | Explanation |
|---------|-------------|
| **Leaking internals** | Entity has `version`, `@Id strategy`, JPA annotations — clients shouldn't see these |
| **Tight coupling** | If you rename a DB column, your API contract changes |
| **Over-exposing data** | Entity might have `password` or `internalNotes` fields |
| **Circular references** | Entity relationships cause infinite JSON serialization loops |

### How It Works in This Project

```
Client → CreateAccountRequest (DTO) → Service → Account (Entity) → Database
Database → Account (Entity) → Mapper → AccountResponse (DTO) → Client
```

**Request DTO** (what the client sends):
```java
public class CreateAccountRequest {
    private String holderName;       // Client provides this
    private BigDecimal initialDeposit;
    private String currency;
    // Note: no id, version, status, createdAt — those are set internally
}
```

**Entity** (what the database stores):
```java
@Entity
public class Account {
    private UUID id;            // Generated by DB
    private String holderName;
    private BigDecimal balance;
    private Long version;       // For optimistic locking — never sent to client
    // ... more internal fields
}
```

**Response DTO** (what the client receives):
```java
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String holderName;
    private BigDecimal balance;
    // Note: no `version` field — it's an implementation detail
}
```

### Interview-Ready Explanation

> "We use DTOs to decouple our API contract from our database entities. Request DTOs define what clients must provide (with validation annotations). Response DTOs define what clients receive (hiding internals like version fields). Mapper classes handle the conversion. This prevents leaking internal details, enables independent evolution of the API and database schema, and avoids circular JSON serialization issues."

---

## 11. Repository Pattern

### What It Is

The Repository Pattern abstracts data access behind an interface, so the service layer doesn't need to know whether data comes from a SQL database, NoSQL store, API, or in-memory cache.

### How It Works in This Project

```java
// AccountRepository.java — this is the INTERFACE
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);
}
```

Spring Data JPA **automatically generates the implementation** based on method names:
- `findByAccountNumber` → `SELECT * FROM accounts WHERE account_number = ?`
- `existsByAccountNumber` → `SELECT COUNT(*) FROM accounts WHERE account_number = ?`

You never write the SQL or the implementation class — Spring does it for you.

### Interview-Ready Explanation

> "We use Spring Data JPA repositories which auto-generate SQL implementations from method names. The service layer programs to the repository interface, not the database. This makes testing easy (mock the interface) and allows database swaps without changing business logic."

---

## 12. ORM (Object-Relational Mapping)

### What It Is

An ORM maps Java objects to database tables, so you work with objects instead of writing raw SQL.

### Without ORM (Raw JDBC)

```java
// You have to write SQL, handle ResultSets, manage connections...
String sql = "SELECT * FROM accounts WHERE id = ?";
PreparedStatement ps = connection.prepareStatement(sql);
ps.setString(1, id.toString());
ResultSet rs = ps.executeQuery();
if (rs.next()) {
    Account account = new Account();
    account.setId(UUID.fromString(rs.getString("id")));
    account.setHolderName(rs.getString("holder_name"));
    // ... 10 more fields
}
```

### With ORM (Hibernate/JPA)

```java
// One line does everything
Account account = accountRepository.findById(id).orElseThrow();
```

### How Hibernate Knows Which Table to Use

```java
@Entity
@Table(name = "accounts")  // ← Maps to the "accounts" table
public class Account {

    @Id
    @Column(name = "id")  // Maps to "id" column
    private UUID id;

    @Column(name = "holder_name")  // Maps to "holder_name" column
    private String holderName;
}
```

### Interview-Ready Explanation

> "We use Hibernate as our ORM, which maps Java entities to PostgreSQL tables. It handles SQL generation, connection management, and object-relational conversion automatically. The `@Entity`, `@Table`, and `@Column` annotations define the mapping. This eliminates boilerplate JDBC code and reduces the chance of SQL injection."

---

## 13. Database Indexing

### What It Is

An index is a data structure (usually a B-tree) that speeds up queries by allowing the database to find rows without scanning the entire table.

### Without Index (Full Table Scan)

```sql
SELECT * FROM transactions WHERE source_account_id = 'abc-123';
-- Database scans ALL 10 million rows 😱
-- Time: 5-30 seconds
```

### With Index

```sql
CREATE INDEX idx_transactions_source ON transactions(source_account_id);

SELECT * FROM transactions WHERE source_account_id = 'abc-123';
-- Database looks up the B-tree, finds rows in milliseconds
-- Time: 1-5 milliseconds
```

### Indexes in This Project

```sql
-- schema.sql
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
-- ↑ Speeds up: GET /api/v1/accounts/number/ACC-100001

CREATE INDEX idx_transactions_source_account_id ON transactions(source_account_id);
CREATE INDEX idx_transactions_target_account_id ON transactions(target_account_id);
-- ↑ Speeds up: GET /api/v1/transactions/account/{id} (transaction history)

CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);
-- ↑ Speeds up: ORDER BY created_at DESC (sorting by newest first)

CREATE INDEX idx_idempotency_keys_key ON idempotency_keys(idempotency_key);
-- ↑ Speeds up: Idempotency key lookup (checked on every mutation request)

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
-- ↑ Speeds up: Cleanup of expired records (scheduled job)
```

### Trade-offs of Indexes

| Pro | Con |
|-----|-----|
| Dramatically speeds up SELECT queries | Slows down INSERT/UPDATE (index must be updated too) |
| Makes ORDER BY fast | Uses extra disk space |
| Enables instant lookups | Too many indexes on a table hurts write performance |

**Rule of thumb**: Index columns that appear in WHERE, JOIN, and ORDER BY clauses. Don't index columns with very low cardinality (like a boolean `is_active` with only 2 values).

### Interview-Ready Explanation

> "We index columns that are frequently queried: account_number for lookups, source/target account IDs for transaction history, idempotency_key for duplicate detection, and created_at for sorted pagination. We chose not to over-index — each index speeds up reads but slightly slows writes, and in a financial system, we need a good balance of both."

---

## 14. Database Constraints

### What It Is

Constraints are rules enforced by the database itself, providing a safety net even if application code has bugs.

### Constraints in This Project

```sql
-- UNIQUE: No two accounts can have the same number
CONSTRAINT uq_accounts_account_number UNIQUE (account_number)

-- CHECK: Balance can never go negative
CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)

-- CHECK: Transaction amounts must be positive
CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)

-- FOREIGN KEY: source_account_id must reference a real account
CONSTRAINT fk_transactions_source_account
    FOREIGN KEY (source_account_id) REFERENCES accounts(id)

-- UNIQUE: Each idempotency key can only exist once
CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
```

### Why Both Java AND Database Constraints?

This is **defense in depth** (concept #21). Even if there's a bug in the Java code that somehow bypasses the balance check, the database will reject the UPDATE:

```
Java check:  if (balance < amount) throw InsufficientBalanceException
DB check:    CHECK (balance >= 0) — the ULTIMATE safety net
```

### Interview-Ready Explanation

> "We enforce constraints at both the application level and the database level. Java code checks balance before withdrawal for user-friendly error messages. The database has CHECK constraints as a safety net. This defense-in-depth approach means a bug in Java code can't corrupt the database."

---

## 15. Connection Pooling

### What It Is

A connection pool maintains a reusable set of database connections. Instead of opening a new connection for every request (expensive — takes 200-500ms), requests borrow an existing connection from the pool and return it when done.

### How It Works in This Project

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # Max 10 connections in the pool
      minimum-idle: 5           # Keep at least 5 ready
      idle-timeout: 30000       # Close idle connections after 30s
      connection-timeout: 20000 # Wait max 20s for a connection
      max-lifetime: 1800000     # Replace connections after 30 minutes
```

We use **HikariCP** (the fastest Java connection pool, default in Spring Boot).

### Why These Settings Matter

| Setting | Effect of Too Low | Effect of Too High |
|---------|-------------------|-------------------|
| `maximum-pool-size` | Requests queue up waiting for a connection | Too many DB connections waste memory |
| `connection-timeout` | Legit requests time out during peak | Slow requests hang silently |
| `idle-timeout` | Resources wasted on unused connections | Connections closed too aggressively |

### Interview-Ready Explanation

> "We use HikariCP connection pooling with a pool of 5-10 connections. This avoids the overhead of opening a new database connection per request (which takes hundreds of milliseconds). Connections are reused, and the pool size is tuned to match expected concurrency."

---

## 16. Exception Handling Strategy

### What It Is

A centralized approach to catching and transforming exceptions into structured HTTP responses.

### How It Works in This Project

```java
// GlobalExceptionHandler.java
@RestControllerAdvice  // ← Intercepts exceptions from ALL controllers
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(...) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Balance", ...);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(...) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ...);
    }

    @ExceptionHandler(Exception.class)  // Catch-all
    public ResponseEntity<ErrorResponse> handleGeneral(...) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", ...);
    }
}
```

**Every error response follows the same structure**:
```json
{
  "status": 422,
  "error": "Insufficient Balance",
  "message": "Available: 5000, Requested: 10000",
  "path": "/api/v1/transactions/withdraw",
  "timestamp": "2026-04-05T10:00:00"
}
```

### Interview-Ready Explanation

> "We use `@RestControllerAdvice` for centralized exception handling. Each custom exception maps to a specific HTTP status code and structured error response. This keeps controllers clean (no try-catch blocks), ensures consistent error formats, centralizes logging, and prevents leaking internal details like stack traces to clients."

---

## 17. API Design (RESTful APIs)

### What It Is

REST (Representational State Transfer) is an architectural style for designing web APIs using standard HTTP methods.

### REST Principles Used in This Project

| Principle | How We Follow It |
|-----------|-----------------|
| **Resource-based URLs** | `/api/v1/accounts` (not `/getAccounts` or `/createAccount`) |
| **HTTP verbs for actions** | GET = read, POST = create |
| **Stateless** | Server stores no session; each request is self-contained |
| **Consistent response format** | All responses use `ApiResponse<T>` wrapper |
| **Proper status codes** | 201 for created, 404 for not found, 422 for business rule violation |
| **Versioning** | `/api/v1/` prefix allows backward-compatible evolution |
| **Pagination** | `?page=0&size=10` for large result sets |

### Interview-Ready Explanation

> "Our API follows RESTful design with resource-based URLs, proper HTTP methods, consistent response wrappers, meaningful status codes, and API versioning via URL path. Mutation endpoints require idempotency keys for safe retries."

---

## 18. Request Validation

### What It Is

Validating request data at the API boundary **before** it reaches business logic. Invalid requests are rejected immediately with helpful error messages.

### How It Works

```java
// On the DTO (request class)
public class DepositRequest {
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be greater than 0")
    private BigDecimal amount;
}

// On the controller (triggers validation)
@PostMapping("/deposit")
public ResponseEntity<?> deposit(@Valid @RequestBody DepositRequest request) {
    //                             ^^^^^
    // @Valid triggers Bean Validation BEFORE the method body runs
}
```

### Interview-Ready Explanation

> "We use Bean Validation annotations (`@NotNull`, `@DecimalMin`, `@Size`) on request DTOs and `@Valid` on controller parameters. Invalid requests are rejected before reaching the service layer, with detailed error messages returned to the client."

---

## 19. Pagination

### What It Is

Returning large datasets in pages rather than all at once. Essential for performance with unbounded result sets.

### How It Works

```java
// Controller
@GetMapping("/account/{accountId}")
public ResponseEntity<?> getHistory(
    @PathVariable UUID accountId,
    @RequestParam(defaultValue = "0") int page,    // Which page (0-indexed)
    @RequestParam(defaultValue = "20") int size) {  // Items per page

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<TransactionResponse> history = transactionService.getTransactionHistory(accountId, pageable);
}
```

**API call**: `GET /api/v1/transactions/account/{id}?page=0&size=10`

**Response includes pagination metadata**:
```json
{
  "content": [...],       // The actual data
  "totalElements": 150,  // Total items across all pages
  "totalPages": 15,      // Total number of pages
  "number": 0,           // Current page number
  "size": 10             // Items per page
}
```

---

## 20. Hashing (SHA-256)

### What It Is

A hash function transforms input data into a fixed-size string (digest). The same input always produces the same hash, but you can't reverse-engineer the input from the hash.

### How We Use It

We hash request bodies to detect if an idempotency key is being reused with different data:

```java
// IdempotencyService.java
public String hashRequest(String requestBody) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
}

// Input:  '{"accountId":"abc","amount":500}'
// Output: "a3f2b8c9d4e1f0a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0"  ← always the same for this input
```

---

## 21. Defense in Depth

### What It Is

A security strategy where multiple independent layers of protection guard against failures. If one layer fails, the next one catches the problem.

### How We Use It

```
Layer 1: Client-side validation (form checks before sending)
Layer 2: @Valid Bean Validation (Spring rejects invalid requests)
Layer 3: Service layer checks (if balance < amount → throw exception)
Layer 4: Database CHECK constraint (CHECK balance >= 0)
Layer 5: Database UNIQUE constraint (prevents duplicate idempotency keys)
```

If a bug in Layer 3 somehow allows a negative balance, Layer 4 catches it. Even if all Java code is bypassed (direct SQL injection), Layer 4 and 5 prevent data corruption.

---

## 22. Structured Logging

### What It Is

Logging important events in a consistent, searchable format with context (transaction ID, account ID, amounts).

### How We Use It

```java
log.info("Transfer successful | TX: {} | From: {} ({}) | To: {} ({}) | Amount: {}",
    saved.getId(),
    source.getId(), source.getBalance(),
    target.getId(), target.getBalance(),
    request.getAmount());
```

Output:
```
2026-04-05 10:30:00.123 [http-nio-8080-1] INFO  TransactionService -
  Transfer successful | TX: abc-123 | From: def-456 ($7500) | To: ghi-789 ($7000) | Amount: 2000
```

### Why This Matters

In production, when something goes wrong at 3 AM, you search the logs. Structured logs let you:
- `grep "TX: abc-123"` → Find everything about one transaction
- `grep "Insufficient balance"` → Find all failed withdrawals
- `grep "All retries exhausted"` → Find all unresolved conflicts

---

## 23. AOP (Aspect-Oriented Programming)

### What It Is

AOP lets you add behavior to methods **without modifying the method itself**. Spring uses AOP for `@Transactional`, `@Retryable`, `@Cacheable`, etc.

### How It Works

When Spring sees `@Transactional` on a method, it creates a **proxy** around the class:

```
Without AOP:
  Client → TransactionService.transfer()

With AOP:
  Client → Spring Proxy → { BEGIN; TransactionService.transfer(); COMMIT; }
```

The proxy adds behavior (begin transaction, catch exceptions, commit/rollback) without changing the original method. That's why `@Retryable` must be on a different class — self-invocation bypasses the proxy.

---

## 24. Dependency Injection

### What It Is

Instead of a class creating its own dependencies, they are **injected** from outside (by Spring).

```java
// ❌ Without DI — tightly coupled, hard to test
public class TransactionService {
    private AccountRepository repo = new AccountRepositoryImpl();  // Creates its own dependency
}

// ✅ With DI — loosely coupled, easy to test
@Service
@RequiredArgsConstructor  // Lombok generates a constructor
public class TransactionService {
    private final AccountRepository repo;  // Injected by Spring
}
```

### Why It Matters for Testing

```java
// In tests, inject a mock instead of the real database
@Mock
private AccountRepository accountRepository;

@InjectMocks
private TransactionService transactionService;

// Now you can test TransactionService without a database!
```

---

## 25. Database Normalization

### What It Is

Organizing database tables to minimize redundancy and dependency. Each piece of data is stored in exactly one place.

### How We Use It

Instead of storing the account holder's name on every transaction row:

```
❌ Denormalized (data duplication):
transactions table:
| id | amount | source_name   | target_name |
|    | 500    | Alice Johnson | Bob Smith   |  ← names repeated everywhere!
```

We store a reference (foreign key) to the accounts table:

```
✅ Normalized (no duplication):
transactions table:
| id | amount | source_account_id | target_account_id |
|    | 500    | uuid-of-alice      | uuid-of-bob       |

accounts table:
| id            | holder_name   |
| uuid-of-alice | Alice Johnson |  ← name stored ONCE
| uuid-of-bob   | Bob Smith     |
```

**Benefits**:
- If Alice changes her name, update ONE row (not millions of transaction rows)
- No data inconsistency (her name can't be "Alice" in one transaction and "Alicia" in another)
- Less storage space

---

## 🎯 Summary Table

| # | Concept | One-Line Summary | Where in Project |
|---|---------|-----------------|-----------------|
| 1 | ACID | Atomic, Consistent, Isolated, Durable transactions | `@Transactional` on services |
| 2 | DB Transactions | Group of SQL ops that all succeed or all fail | `@Transactional` annotation |
| 3 | Optimistic Locking | Version check at write time, retry on conflict | `@Version` on Account |
| 4 | Pessimistic Locking | Lock at read time (not used, but explained) | — |
| 5 | Idempotency | Same request twice = same result once | `IdempotencyService` + header |
| 6 | Concurrency Control | Safe handling of simultaneous access | Version + Retry + Constraints |
| 7 | Deadlock Prevention | Consistent lock ordering prevents circular wait | Sorted UUID order in transfer |
| 8 | Retry + Backoff | Auto-retry with increasing delays | `@Retryable` with `@Backoff` |
| 9 | 3-Tier Architecture | Controller → Service → Repository | Package structure |
| 10 | DTO Pattern | Separate objects for API vs database | `dto/request/` and `dto/response/` |
| 11 | Repository Pattern | Abstracted data access interface | `repository/` package |
| 12 | ORM | Objects mapped to database tables | Hibernate/JPA annotations |
| 13 | Indexing | Speed up queries with data structures | `CREATE INDEX` in schema.sql |
| 14 | Constraints | Database-level data integrity rules | `CHECK`, `UNIQUE`, `FK` |
| 15 | Connection Pooling | Reusable database connections | HikariCP in application.yml |
| 16 | Exception Handling | Centralized, structured error responses | `GlobalExceptionHandler` |
| 17 | REST API Design | Standard HTTP methods + resource URLs | Controllers |
| 18 | Request Validation | Reject invalid data at API boundary | `@Valid` + Bean Validation |
| 19 | Pagination | Return large datasets in pages | `Pageable` + `Page<T>` |
| 20 | SHA-256 Hashing | Fingerprint request bodies for comparison | `IdempotencyService` |
| 21 | Defense in Depth | Multiple independent safety layers | Java checks + DB constraints |
| 22 | Structured Logging | Consistent, searchable log format | SLF4J + Logback |
| 23 | AOP | Add behavior without modifying code | `@Transactional`, `@Retryable` |
| 24 | Dependency Injection | Framework provides dependencies | Spring `@Autowired` / constructor |
| 25 | Normalization | No redundant data in tables | Foreign keys instead of duplicated fields |
