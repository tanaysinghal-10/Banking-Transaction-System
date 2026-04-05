# 🧪 Step-by-Step Testing Guide

## Complete Hands-On Walkthrough of the Banking Transaction System

This guide walks you through **every feature** of the application with real commands you can copy-paste. Each test includes **both PowerShell (curl) and Postman** instructions. By the end, you'll understand exactly how deposits, withdrawals, transfers, idempotency, and error handling work.

---

## 📋 Table of Contents

1. [Prerequisites & Setup](#-step-0-prerequisites--setup)
2. [Swagger UI & Postman Setup](#-swagger-ui--postman-setup)
3. [Test 1: Create Your First Account](#-test-1-create-your-first-account)
4. [Test 2: Create a Second Account](#-test-2-create-a-second-account)
5. [Test 3: View Account Details](#-test-3-view-account-details)
6. [Test 4: Deposit Money](#-test-4-deposit-money)
7. [Test 5: Withdraw Money](#-test-5-withdraw-money)
8. [Test 6: Transfer Money Between Accounts](#-test-6-transfer-money-between-accounts)
9. [Test 7: View Transaction History](#-test-7-view-transaction-history)
10. [Test 8: Idempotency — Preventing Duplicate Transactions](#-test-8-idempotency--preventing-duplicate-transactions)
11. [Test 9: Insufficient Balance Error](#-test-9-insufficient-balance-error)
12. [Test 10: Account Not Found Error](#-test-10-account-not-found-error)
13. [Test 11: Self-Transfer Error](#-test-11-self-transfer-error)
14. [Test 12: Missing Idempotency Key Error](#-test-12-missing-idempotency-key-error)
15. [Test 13: Validation Errors](#-test-13-validation-errors)
16. [Test 14: Idempotency Key Reuse with Different Data](#-test-14-idempotency-key-reuse-with-different-data)
17. [Test 15: End-to-End Scenario — Full Banking Day](#-test-15-end-to-end-scenario--full-banking-day)
18. [Running the Automated Tests](#-running-the-automated-tests)
19. [Quick Reference Card](#-quick-reference-card)

---

## 🔧 Step 0: Prerequisites & Setup

### What You Need Installed

| Tool | What It Is | How to Check |
|------|-----------|-------------|
| **Java 17** | The programming language runtime | Open terminal → type `java -version` → should show `17.x.x` |
| **Docker Desktop** | Runs our database in a container | Open terminal → type `docker --version` |
| **curl** | A tool to send HTTP requests from terminal | Open terminal → type `curl --version` |
| **Postman** *(optional)* | GUI tool for API testing | Download from [postman.com](https://www.postman.com/downloads/) |

> **💡 What is curl?**
> curl is a command-line tool that lets you send HTTP requests — the same kind your browser sends when you visit a website. We use it instead of a browser because our application is a backend API (no web pages, just data).

### Step 0.1: Start the Database

Open a terminal (PowerShell on Windows) and navigate to the project folder:

```powershell
cd "c:\Users\tanay\Downloads\Rebuild\Projects\Banking Application"
```

Start PostgreSQL using Docker:

```powershell
docker compose up -d
```

**What this does**: Downloads and starts a PostgreSQL database in the background. Think of it like starting up a filing cabinet where all our bank data will be stored.

Verify it's running:

```powershell
docker compose ps
```

You should see `banking-db` with status `running`.

### Step 0.2: Start the Application

In the same terminal (or a new one), run:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.6\bin\mvn.cmd" spring-boot:run
```

**What this does**: Starts our Banking application. It connects to the PostgreSQL database, creates all the tables, and inserts 3 sample accounts.

Wait until you see a line like:

```
Started BankingApplication in X.XXX seconds
```

> **⚠️ Keep this terminal open!** The application runs here. Open a **NEW terminal** for the test commands below.

### Step 0.3: Open a New Terminal for Testing

Open a second PowerShell terminal. All the `curl` commands below will be run in this new terminal.

> **💡 Note for Windows**: On Windows PowerShell, `curl` is actually an alias for `Invoke-WebRequest`. To use real curl syntax, either:
> - Use `curl.exe` instead of `curl` in all commands below, OR
> - Use Git Bash if you have Git installed

**All commands below use `curl.exe` to be safe on Windows.**

---

## 🔶 Swagger UI & Postman Setup

### Swagger UI (Built-in API Explorer)

Once the application is running, open your browser and go to:

> **http://localhost:8080/swagger-ui.html**

This gives you an interactive web page where you can:
- See all API endpoints with their request/response schemas
- Try out any endpoint directly in the browser
- View the full OpenAPI specification

### Import All Endpoints into Postman (One-Click)

1. Open **Postman**
2. Click **Import** (top-left corner)
3. Select the **Link** tab
4. Paste this URL:
   ```
   http://localhost:8080/v3/api-docs
   ```
5. Click **Import**
6. ✅ All 7 endpoints are now in Postman with proper methods, URLs, headers, and request body schemas!

> **💡 Tip**: After importing, Postman creates a collection called "Banking Transaction System API". All endpoints are pre-configured with the correct HTTP method and URL. You just need to fill in the request body and headers for each test below.

### Postman Environment Setup (Optional but Recommended)

To avoid copy-pasting UUIDs everywhere, set up a Postman Environment:

1. Click **Environments** (left sidebar) → **+** (create new)
2. Name it: `Banking Local`
3. Add these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `base_url` | `http://localhost:8080` | API base URL |
| `rahul_id` | *(fill after Test 1)* | Rahul's account UUID |
| `priya_id` | *(fill after Test 2)* | Priya's account UUID |
| `alice_id` | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | Pre-seeded Alice |
| `bob_id` | `b2c3d4e5-f6a7-8901-bcde-f12345678901` | Pre-seeded Bob |
| `charlie_id` | `c3d4e5f6-a7b8-9012-cdef-123456789012` | Pre-seeded Charlie |

4. Select this environment from the top-right dropdown in Postman.

Now you can use `{{base_url}}`, `{{rahul_id}}`, etc. in all your requests!

---

## ✅ Test 1: Create Your First Account

**What we're testing**: Can the system create a new bank account?

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{\"holderName\": \"Rahul Sharma\", \"initialDeposit\": 10000.00, \"currency\": \"USD\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/accounts` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |

**Body** (select `raw` → `JSON`):

```json
{
  "holderName": "Rahul Sharma",
  "initialDeposit": 10000.00,
  "currency": "USD"
}
```

### ✅ Expected Response (201 Created)

```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "id": "some-uuid-will-be-here",
    "accountNumber": "ACC-XXXXXX",
    "holderName": "Rahul Sharma",
    "balance": 10000.00,
    "currency": "USD",
    "status": "ACTIVE",
    "createdAt": "2026-04-05T...",
    "updatedAt": "2026-04-05T..."
  },
  "timestamp": "2026-04-05T..."
}
```

**📝 IMPORTANT: Copy the `id` value from the response!** You'll need it for the next tests. If using Postman environments, update the `rahul_id` variable with this value.

**What just happened behind the scenes**:
1. The request arrived at `AccountController`
2. Spring validated the request body (checked that `holderName` is not blank, etc.)
3. `AccountService.createAccount()` generated a unique account number
4. Hibernate saved the account to the `accounts` table in PostgreSQL
5. The entity was converted to a DTO (hiding internal fields like `version`) and returned

---

## ✅ Test 2: Create a Second Account

**What we're testing**: Creating another account so we can transfer money between them later.

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{\"holderName\": \"Priya Patel\", \"initialDeposit\": 5000.00, \"currency\": \"USD\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/accounts` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |

**Body** (raw JSON):

```json
{
  "holderName": "Priya Patel",
  "initialDeposit": 5000.00,
  "currency": "USD"
}
```

**📝 Copy the `id` from this response too!** Update Postman environment variable `priya_id`. We now have:
- **Rahul's account ID**: `<paste-rahul-id-here>`
- **Priya's account ID**: `<paste-priya-id-here>`

---

## ✅ Test 3: View Account Details

**What we're testing**: Can we look up an account by its ID?

### 3a. Get Account by ID

#### 🖥️ PowerShell (curl)

Replace `<RAHUL_ID>` with Rahul's actual UUID from Test 1:

```powershell
curl.exe http://localhost:8080/api/v1/accounts/<RAHUL_ID>
```

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/accounts/{{rahul_id}}` |

No headers or body needed for GET requests.

**What you should see**: Rahul's account with balance $10,000.00

### 3b. View One of the Pre-Seeded Accounts

#### 🖥️ PowerShell (curl)

```powershell
curl.exe http://localhost:8080/api/v1/accounts/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/accounts/a1b2c3d4-e5f6-7890-abcd-ef1234567890` |

**What you should see**: Alice Johnson's account with $5,000.00 balance.

### 3c. Get Account by Account Number

#### 🖥️ PowerShell (curl)

```powershell
curl.exe http://localhost:8080/api/v1/accounts/number/ACC-100001
```

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/accounts/number/ACC-100001` |

**What you should see**: Same Alice Johnson account (looked up by account number instead of ID).

---

## ✅ Test 4: Deposit Money

**What we're testing**: Can we add money to an account?

### 🖥️ PowerShell (curl)

Replace `<RAHUL_ID>` with Rahul's actual UUID:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: deposit-test-001" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 2500.00, \"description\": \"Freelance payment received\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `deposit-test-001` |

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 2500.00,
  "description": "Freelance payment received"
}
```

> 💡 In Postman with environment: use `{{rahul_id}}` instead of `<RAHUL_ID>` in the body.

### ✅ Expected Response (201 Created)

```json
{
  "success": true,
  "message": "Deposit completed successfully",
  "data": {
    "id": "some-transaction-uuid",
    "type": "DEPOSIT",
    "amount": 2500.00,
    "sourceAccountId": null,
    "targetAccountId": "<RAHUL_ID>",
    "status": "SUCCESS",
    "description": "Freelance payment received",
    "createdAt": "..."
  }
}
```

**Key things to notice**:
- `sourceAccountId` is **null** — because in a deposit, money comes from outside the system
- `targetAccountId` is Rahul's ID — the money went INTO his account
- `type` is `DEPOSIT`
- `status` is `SUCCESS`

**Verify the balance changed** — Rahul started with $10,000, deposited $2,500:

| | PowerShell | Postman |
|-|-----------|---------|
| | `curl.exe http://localhost:8080/api/v1/accounts/<RAHUL_ID>` | `GET http://localhost:8080/api/v1/accounts/{{rahul_id}}` |

**Expected balance**: `12500.00` ✅

**What happened behind the scenes**:
1. Controller checked for the `Idempotency-Key` header → found `deposit-test-001`
2. `RetryableTransactionService` checked if this key was already processed → **No, it's new**
3. `TransactionService.deposit()` loaded Rahul's account, added $2,500 to the balance
4. Hibernate generated: `UPDATE accounts SET balance=12500, version=version+1 WHERE id=? AND version=?`
5. Transaction record was saved to the `transactions` table
6. The idempotency key and response were cached for 24 hours

---

## ✅ Test 5: Withdraw Money

**What we're testing**: Can we take money out of an account?

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/withdraw `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: withdraw-test-001" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 3000.00, \"description\": \"Rent payment\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/withdraw` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `withdraw-test-001` |

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 3000.00,
  "description": "Rent payment"
}
```

### ✅ Expected Response

- `type`: `WITHDRAWAL`
- `sourceAccountId`: Rahul's ID (money is coming FROM his account)
- `targetAccountId`: **null** (money leaves the system)

**Verify**: `GET /api/v1/accounts/<RAHUL_ID>` → **Expected balance**: `12500 - 3000 = 9500.00` ✅

---

## ✅ Test 6: Transfer Money Between Accounts

**What we're testing**: Can we move money from one account to another? This is the most complex operation — it debits one account and credits another within a single transaction.

### 🖥️ PowerShell (curl)

Replace both `<RAHUL_ID>` and `<PRIYA_ID>` with actual UUIDs:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/transfer `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: transfer-test-001" `
  -d '{\"sourceAccountId\": \"<RAHUL_ID>\", \"targetAccountId\": \"<PRIYA_ID>\", \"amount\": 2000.00, \"description\": \"Splitting dinner bill\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/transfer` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `transfer-test-001` |

**Body** (raw JSON):

```json
{
  "sourceAccountId": "<RAHUL_ID>",
  "targetAccountId": "<PRIYA_ID>",
  "amount": 2000.00,
  "description": "Splitting dinner bill"
}
```

### ✅ Expected Response

- `type`: `TRANSFER`
- `sourceAccountId`: Rahul's ID
- `targetAccountId`: Priya's ID
- `amount`: 2000.00
- `status`: `SUCCESS`

**Verify both balances changed**:

| Account | Check URL | Expected Balance |
|---------|-----------|-----------------|
| Rahul (was $9,500, sent $2,000) | `GET /api/v1/accounts/<RAHUL_ID>` | `7500.00` ✅ |
| Priya (was $5,000, received $2,000) | `GET /api/v1/accounts/<PRIYA_ID>` | `7000.00` ✅ |

**What happened behind the scenes** (this is the most important flow to understand):

```
Step 1: Controller validates the request and extracts the Idempotency-Key
Step 2: RetryableTransactionService checks if "transfer-test-001" was already processed → No
Step 3: TransactionService.transfer() starts a @Transactional block
Step 4: Accounts are sorted by UUID to prevent deadlocks
        (If UUID of Rahul < UUID of Priya, Rahul is loaded first)
Step 5: Both accounts are loaded from DB (Hibernate reads version field)
Step 6: Both accounts validated as ACTIVE
Step 7: Check: Does Rahul have ≥ $2,000? Yes (he has $9,500)
Step 8: Rahul's balance: 9500 - 2000 = 7500
Step 9: Priya's balance: 5000 + 2000 = 7000
Step 10: Hibernate generates two UPDATEs:
         UPDATE accounts SET balance=7500, version=1 WHERE id=rahul AND version=0
         UPDATE accounts SET balance=7000, version=1 WHERE id=priya AND version=0
Step 11: Transaction record saved
Step 12: All changes COMMITTED (atomic — either all happen or none)
Step 13: Response cached with idempotency key
```

---

## ✅ Test 7: View Transaction History

**What we're testing**: Can we see all transactions for a specific account?

### 🖥️ PowerShell (curl)

```powershell
curl.exe "http://localhost:8080/api/v1/transactions/account/<RAHUL_ID>?page=0&size=10"
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/transactions/account/<RAHUL_ID>` |

**Query Params** (add in the Params tab):

| Key | Value |
|-----|-------|
| `page` | `0` |
| `size` | `10` |

### ✅ Expected Response

```json
{
  "success": true,
  "data": {
    "content": [
      { "type": "TRANSFER", "amount": 2000.00, "description": "Splitting dinner bill" },
      { "type": "WITHDRAWAL", "amount": 3000.00, "description": "Rent payment" },
      { "type": "DEPOSIT", "amount": 2500.00, "description": "Freelance payment received" }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "number": 0,
    "size": 10
  }
}
```

**Key things to notice**:
- Transactions are sorted **newest first** (the transfer appears first)
- The pagination info shows `totalElements`, `totalPages`, current `number` (page), and `size`
- Both transactions where Rahul is the source AND where he's the target appear here

---

## ✅ Test 8: Idempotency — Preventing Duplicate Transactions

**What we're testing**: If you accidentally send the same request twice (e.g., double-click a "Pay" button), does the system charge you twice? **It should NOT.**

### 8a. First Request — Normal Processing

#### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: idempotency-demo-key-999" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 500.00, \"description\": \"Bonus payment\"}'
```

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `idempotency-demo-key-999` |

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 500.00,
  "description": "Bonus payment"
}
```

**Verify balance**: `GET /api/v1/accounts/<RAHUL_ID>` → **Expected**: `7500 + 500 = 8000.00`

### 8b. Second Request — SAME Key, SAME Body (Duplicate)

Now send the **exact same request** again. In Postman, just click **Send** again without changing anything.

In PowerShell, run the exact same curl command again:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: idempotency-demo-key-999" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 500.00, \"description\": \"Bonus payment\"}'
```

**What you should see**: The **exact same response** as the first request (same transaction ID, same everything).

**Check the balance again**: `GET /api/v1/accounts/<RAHUL_ID>` → **Expected**: Still `8000.00` ✅ — **NOT** `8500.00`!

**🎉 The duplicate was prevented!** The system recognized the idempotency key, found the cached response, and returned it without processing a second deposit.

### How This Works

```
First Request:
  1. Check DB for key "idempotency-demo-key-999" → NOT FOUND
  2. Process the deposit → balance becomes $8,000
  3. Save response in idempotency_keys table with SHA-256 hash of the request body
  4. Return response

Second Request (duplicate):
  1. Check DB for key "idempotency-demo-key-999" → FOUND!
  2. Compare SHA-256 hash of request body → MATCHES
  3. Return the CACHED response from step 3 above
  4. No deposit is processed
```

---

## ✅ Test 9: Insufficient Balance Error

**What we're testing**: What happens when you try to withdraw more money than you have?

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/withdraw `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: insufficient-test-001" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 999999.00, \"description\": \"Trying to withdraw too much\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/withdraw` |

**Headers:**

| Key | Value |
|-----|-------|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `insufficient-test-001` |

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 999999.00,
  "description": "Trying to withdraw too much"
}
```

### ❌ Expected Response (422 Unprocessable Entity)

```json
{
  "status": 422,
  "error": "Insufficient Balance",
  "message": "Insufficient balance. Available: 8000.0000, Requested: 999999.00",
  "path": "/api/v1/transactions/withdraw",
  "timestamp": "..."
}
```

**Key things to notice**:
- The error message tells you exactly how much you have and how much you tried to withdraw
- HTTP status 422 = "I understood your request, but the business rules won't allow it"
- Rahul's balance is **unchanged** — the transaction was never committed

---

## ✅ Test 10: Account Not Found Error

**What we're testing**: What happens when you use a fake account ID?

### 🖥️ PowerShell (curl)

```powershell
curl.exe http://localhost:8080/api/v1/accounts/00000000-0000-0000-0000-000000000000
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/accounts/00000000-0000-0000-0000-000000000000` |

### ❌ Expected Response (404 Not Found)

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Account with ID '00000000-0000-0000-0000-000000000000' not found",
  "path": "/api/v1/accounts/00000000-0000-0000-0000-000000000000",
  "timestamp": "..."
}
```

**Also try a deposit to a fake account**:

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:** `Content-Type: application/json`, `Idempotency-Key: notfound-test-001`

**Body:**
```json
{
  "accountId": "00000000-0000-0000-0000-000000000000",
  "amount": 100.00
}
```

**What you should see**: Same 404 error. You can't deposit into an account that doesn't exist.

---

## ✅ Test 11: Self-Transfer Error

**What we're testing**: What happens when someone tries to transfer money to their own account?

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/transfer `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: self-transfer-test-001" `
  -d '{\"sourceAccountId\": \"<RAHUL_ID>\", \"targetAccountId\": \"<RAHUL_ID>\", \"amount\": 100.00}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/transfer` |

**Headers:** `Content-Type: application/json`, `Idempotency-Key: self-transfer-test-001`

**Body** (raw JSON):

```json
{
  "sourceAccountId": "<RAHUL_ID>",
  "targetAccountId": "<RAHUL_ID>",
  "amount": 100.00
}
```

> ⚠️ Notice: both source and target are the SAME account ID.

### ❌ Expected Response (400 Bad Request)

```json
{
  "status": 400,
  "error": "Invalid Transaction",
  "message": "Cannot transfer to the same account",
  "path": "/api/v1/transactions/transfer",
  "timestamp": "..."
}
```

---

## ✅ Test 12: Missing Idempotency Key Error

**What we're testing**: What happens when a client forgets to send the `Idempotency-Key` header?

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 100.00}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:** Only `Content-Type: application/json` — **do NOT add Idempotency-Key.**

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 100.00
}
```

### ❌ Expected Response (400 Bad Request)

```json
{
  "status": 400,
  "error": "Missing Header",
  "message": "Required header 'Idempotency-Key' is missing",
  "path": "/api/v1/transactions/deposit",
  "timestamp": "..."
}
```

---

## ✅ Test 13: Validation Errors

**What we're testing**: What happens when you send invalid data in the request?

### 13a. Missing Required Fields

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/accounts` |

**Headers:** `Content-Type: application/json`

**Body** (raw JSON):

```json
{}
```

#### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{}'
```

#### ❌ Expected Response (400)

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Request validation failed",
  "details": [
    "holderName: Holder name is required"
  ],
  "path": "/api/v1/accounts",
  "timestamp": "..."
}
```

### 13b. Negative Deposit Amount

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:** `Content-Type: application/json`, `Idempotency-Key: validation-test-002`

**Body:**

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": -500.00
}
```

**What you should see**: Validation error saying amount must be greater than 0.

### 13c. Zero Transfer Amount

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/transfer` |

**Headers:** `Content-Type: application/json`, `Idempotency-Key: validation-test-003`

**Body:**

```json
{
  "sourceAccountId": "<RAHUL_ID>",
  "targetAccountId": "<PRIYA_ID>",
  "amount": 0.00
}
```

**What you should see**: Validation error.

**What happened**: Spring's `@Valid` annotation triggered Bean Validation before the request even reached the service layer. Invalid requests are rejected immediately.

---

## ✅ Test 14: Idempotency Key Reuse with Different Data

**What we're testing**: What happens if a client reuses an idempotency key from a previous request but with **different** data? This is a client bug — the system should catch it.

We already used `deposit-test-001` in Test 4. Let's reuse it with different data:

### 🖥️ PowerShell (curl)

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: deposit-test-001" `
  -d '{\"accountId\": \"<RAHUL_ID>\", \"amount\": 9999.00, \"description\": \"Trying to reuse key with different amount\"}'
```

### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/v1/transactions/deposit` |

**Headers:** `Content-Type: application/json`, `Idempotency-Key: deposit-test-001`

**Body** (raw JSON):

```json
{
  "accountId": "<RAHUL_ID>",
  "amount": 9999.00,
  "description": "Trying to reuse key with different amount"
}
```

> ⚠️ Notice: same Idempotency-Key as Test 4, but the amount is different ($9,999 vs $2,500).

### ❌ Expected Response (409 Conflict)

```json
{
  "status": 409,
  "error": "Duplicate Request",
  "message": "Idempotency key 'deposit-test-001' was already used with a different request body",
  "path": "/api/v1/transactions/deposit",
  "timestamp": "..."
}
```

---

## ✅ Test 15: End-to-End Scenario — Full Banking Day

This test simulates a realistic day of banking operations. Follow each step in order.

### Scenario Setup

Imagine it's payday. Alice gets her salary, pays Bob for freelance work, and Charlie tries to buy something he can't afford.

Using the pre-seeded accounts:
- **Alice**: `a1b2c3d4-e5f6-7890-abcd-ef1234567890` (Balance: $5,000)
- **Bob**: `b2c3d4e5-f6a7-8901-bcde-f12345678901` (Balance: $3,000)
- **Charlie**: `c3d4e5f6-a7b8-9012-cdef-123456789012` (Balance: $1,500)

### Step 1: Alice receives her salary ($8,000 deposit)

#### 📮 Postman

`POST http://localhost:8080/api/v1/transactions/deposit`

**Headers:** `Content-Type: application/json`, `Idempotency-Key: e2e-salary-001`

```json
{
  "accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 8000.00,
  "description": "Monthly salary - April 2026"
}
```

#### 🖥️ PowerShell

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/deposit `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: e2e-salary-001" `
  -d '{\"accountId\": \"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", \"amount\": 8000.00, \"description\": \"Monthly salary - April 2026\"}'
```

**Expected Alice balance**: `5000 + 8000 = 13000.00`

### Step 2: Alice pays Bob for freelance work ($3,500 transfer)

#### 📮 Postman

`POST http://localhost:8080/api/v1/transactions/transfer`

**Headers:** `Content-Type: application/json`, `Idempotency-Key: e2e-pay-bob-001`

```json
{
  "sourceAccountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "targetAccountId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "amount": 3500.00,
  "description": "Freelance payment - website redesign"
}
```

#### 🖥️ PowerShell

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/transfer `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: e2e-pay-bob-001" `
  -d '{\"sourceAccountId\": \"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", \"targetAccountId\": \"b2c3d4e5-f6a7-8901-bcde-f12345678901\", \"amount\": 3500.00, \"description\": \"Freelance payment - website redesign\"}'
```

**Expected**: Alice = `13000 - 3500 = 9500.00`, Bob = `3000 + 3500 = 6500.00`

### Step 3: Bob transfers pocket money to Charlie ($1,000)

#### 📮 Postman

`POST http://localhost:8080/api/v1/transactions/transfer`

**Headers:** `Content-Type: application/json`, `Idempotency-Key: e2e-bob-charlie-001`

```json
{
  "sourceAccountId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "targetAccountId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "amount": 1000.00,
  "description": "Pocket money"
}
```

#### 🖥️ PowerShell

```powershell
curl.exe -X POST http://localhost:8080/api/v1/transactions/transfer `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: e2e-bob-charlie-001" `
  -d '{\"sourceAccountId\": \"b2c3d4e5-f6a7-8901-bcde-f12345678901\", \"targetAccountId\": \"c3d4e5f6-a7b8-9012-cdef-123456789012\", \"amount\": 1000.00, \"description\": \"Pocket money\"}'
```

**Expected**: Bob = `6500 - 1000 = 5500.00`, Charlie = `1500 + 1000 = 2500.00`

### Step 4: Charlie tries to buy a laptop ($5,000 withdrawal) — SHOULD FAIL

#### 📮 Postman

`POST http://localhost:8080/api/v1/transactions/withdraw`

**Headers:** `Content-Type: application/json`, `Idempotency-Key: e2e-charlie-laptop-001`

```json
{
  "accountId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "amount": 5000.00,
  "description": "Laptop purchase"
}
```

**Expected**: ❌ Error! Charlie only has $2,500 but tried to withdraw $5,000.

### Step 5: Charlie withdraws what he can afford ($2,000)

#### 📮 Postman

`POST http://localhost:8080/api/v1/transactions/withdraw`

**Headers:** `Content-Type: application/json`, `Idempotency-Key: e2e-charlie-withdraw-001`

```json
{
  "accountId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "amount": 2000.00,
  "description": "Used laptop purchase"
}
```

**Expected Charlie balance**: `2500 - 2000 = 500.00`

### Step 6: Verify ALL Final Balances

In Postman, send three GET requests:

| Account | URL | Expected Balance |
|---------|-----|-----------------|
| Alice | `GET http://localhost:8080/api/v1/accounts/a1b2c3d4-e5f6-7890-abcd-ef1234567890` | **$9,500.00** |
| Bob | `GET http://localhost:8080/api/v1/accounts/b2c3d4e5-f6a7-8901-bcde-f12345678901` | **$5,500.00** |
| Charlie | `GET http://localhost:8080/api/v1/accounts/c3d4e5f6-a7b8-9012-cdef-123456789012` | **$500.00** |

**Expected Final Balances**:

| Account | Started | Deposits | Sent | Received | Withdrew | Final |
|---------|---------|----------|------|----------|----------|-------|
| Alice | $5,000 | +$8,000 | -$3,500 | — | — | **$9,500** |
| Bob | $3,000 | — | -$1,000 | +$3,500 | — | **$5,500** |
| Charlie | $1,500 | — | — | +$1,000 | -$2,000 | **$500** |

**Total money in the system**: $5,000 + $3,000 + $1,500 + $8,000 (new deposit) - $2,000 (withdrawal) = **$15,500**. This equals $9,500 + $5,500 + $500 = **$15,500** ✅

**Money was neither created nor destroyed** — this is the fundamental invariant of a correct banking system.

### Step 7: View Transaction History for Alice

#### 📮 Postman

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/v1/transactions/account/a1b2c3d4-e5f6-7890-abcd-ef1234567890` |
| **Params** | `page=0`, `size=20` |

#### 🖥️ PowerShell

```powershell
curl.exe "http://localhost:8080/api/v1/transactions/account/a1b2c3d4-e5f6-7890-abcd-ef1234567890?page=0&size=20"
```

You should see Alice's deposit and transfer listed.

---

## 🤖 Running the Automated Tests

The project includes automated tests you can run without the database:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.6\bin\mvn.cmd" test
```

This runs:
- **AccountServiceTest** (6 tests): Tests account creation, retrieval, and error cases
- **TransactionServiceTest** (8 tests): Tests deposits, withdrawals, transfers, and business rule violations
- **ConcurrencyTest** (1 test): Spawns 10 threads doing simultaneous withdrawals to verify optimistic locking works

You should see:

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 📋 Quick Reference Card

### All API Endpoints

| Method | URL | What It Does | Needs Idempotency-Key? |
|--------|-----|-------------|----------------------|
| POST | `/api/v1/accounts` | Create account | No |
| GET | `/api/v1/accounts/{id}` | Get account by UUID | No |
| GET | `/api/v1/accounts/number/{num}` | Get account by number | No |
| POST | `/api/v1/transactions/deposit` | Deposit money | **Yes** |
| POST | `/api/v1/transactions/withdraw` | Withdraw money | **Yes** |
| POST | `/api/v1/transactions/transfer` | Transfer money | **Yes** |
| GET | `/api/v1/transactions/account/{id}` | Transaction history | No |

### Swagger & Documentation URLs

| URL | What It Does |
|-----|-------------|
| `http://localhost:8080/swagger-ui.html` | Interactive API explorer (Swagger UI) |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.0 JSON spec (import into Postman) |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI 3.0 YAML spec |

### Error Codes Summary

| HTTP Code | When It Happens | Example |
|-----------|----------------|---------|
| 200 | Everything's fine | GET account |
| 201 | Resource created | POST new account or transaction |
| 400 | Bad request data | Missing fields, invalid amounts |
| 404 | Resource not found | Fake account ID |
| 409 | Conflict | Idempotency key reuse with different data |
| 422 | Business rule violation | Insufficient balance |
| 500 | Server error | Unexpected crash (shouldn't happen) |

### Pre-Seeded Account IDs

| Account | UUID | Number |
|---------|------|--------|
| Alice | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | ACC-100001 |
| Bob | `b2c3d4e5-f6a7-8901-bcde-f12345678901` | ACC-100002 |
| Charlie | `c3d4e5f6-a7b8-9012-cdef-123456789012` | ACC-100003 |

---

## 🛑 Cleanup

When you're done testing:

```powershell
# Stop the application (Ctrl+C in the terminal where it's running)

# Stop the database
docker compose down

# To also delete all data
docker compose down -v
```
