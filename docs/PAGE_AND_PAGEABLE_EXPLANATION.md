# Understanding Page and Pageable in Spring Data

## What is Pageable?

`Pageable` is Spring Data's interface for pagination and sorting information. It tells the database:
- Which page of results to return (0-based)
- How many records per page
- How to sort the results

## What is Page?

`Page<T>` is Spring Data's interface that wraps the results of a paginated query. It contains:
- The actual data (`List<T> content`)
- Metadata about the pagination

## Example from Your Code

```java
@GetMapping("/account/{accountId}")
public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactionHistory(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,      // Page number (0-based)
        @RequestParam(defaultValue = "20") int size) {   // Records per page

    // Create Pageable with page=0, size=20, sorted by createdAt DESC
    Pageable pageable = PageRequest.of(page, size, 
        Sort.by(Sort.Direction.DESC, "createdAt"));
    
    // Get paginated results
    Page<TransactionResponse> history = transactionService.getTransactionHistory(accountId, pageable);
    
    return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", history));
}
```

## What Page Contains

When you call `transactionService.getTransactionHistory(accountId, pageable)`, the returned `Page<TransactionResponse>` contains:

```java
Page<TransactionResponse> page = // ... result

// The actual transaction data (up to 20 items)
List<TransactionResponse> transactions = page.getContent();

// Pagination metadata
int currentPage = page.getNumber();           // 0
int pageSize = page.getSize();               // 20
long totalElements = page.getTotalElements(); // Total transactions for this account
int totalPages = page.getTotalPages();        // Total number of pages
boolean hasNext = page.hasNext();            // Is there a next page?
boolean hasPrevious = page.hasPrevious();    // Is there a previous page?
boolean isFirst = page.isFirst();            // Is this the first page?
boolean isLast = page.isLast();              // Is this the last page?
```

## API Response Example

For an account with 47 transactions, requesting page 0 with size 20:

```json
{
  "success": true,
  "message": "Transaction history retrieved",
  "data": {
    "content": [
      {
        "id": "txn-001",
        "type": "DEPOSIT",
        "amount": 1000.00,
        "description": "Salary",
        "createdAt": "2024-01-15T10:30:00"
      },
      // ... 19 more transactions
    ],
    "pageable": {
      "page": 0,
      "size": 20,
      "sort": ["createdAt: DESC"]
    },
    "totalElements": 47,
    "totalPages": 3,
    "first": true,
    "last": false,
    "numberOfElements": 20,
    "empty": false
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

## How It Works in the Database

The `Pageable` gets translated to SQL LIMIT and OFFSET:

```sql
-- For page=0, size=20
SELECT t FROM Transaction t 
WHERE t.sourceAccountId = ? OR t.targetAccountId = ? 
ORDER BY t.createdAt DESC
LIMIT 20 OFFSET 0

-- For page=1, size=20  
SELECT t FROM Transaction t 
WHERE t.sourceAccountId = ? OR t.targetAccountId = ?
ORDER BY t.createdAt DESC
LIMIT 20 OFFSET 20
```

## Benefits

1. **Memory Efficient**: Only loads the records you need
2. **Performance**: Database can optimize with LIMIT/OFFSET
3. **User Experience**: Fast loading, even with millions of records
4. **Flexible**: Client can control page size and navigation

## Common Pageable Patterns

```java
// Basic pagination
Pageable pageable = PageRequest.of(0, 10);

// With sorting
Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

// Multiple sort fields
Pageable pageable = PageRequest.of(0, 10, 
    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

// From HTTP parameters
Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
```
