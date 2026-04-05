package com.banking.system.service;

import com.banking.system.dto.request.DepositRequest;
import com.banking.system.dto.request.TransferRequest;
import com.banking.system.dto.request.WithdrawRequest;
import com.banking.system.dto.response.TransactionResponse;
import com.banking.system.exception.*;
import com.banking.system.model.IdempotencyRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Wraps TransactionService with retry logic and idempotency checks.
 *
 * WHY A SEPARATE CLASS?
 *
 * Spring's @Retryable works via AOP proxies. If a @Retryable method calls
 * another method in the same class, the proxy is bypassed and retry doesn't work.
 * By putting retry logic in a separate class that delegates to TransactionService,
 * the AOP proxy correctly intercepts the calls.
 *
 * RETRY STRATEGY:
 *
 * When an OptimisticLockException occurs:
 * 1. First retry: wait 100ms
 * 2. Second retry: wait 200ms (100 * 2.0 multiplier)
 * 3. Third retry: wait 400ms
 * 4. If all 3 retries fail: the exception propagates to GlobalExceptionHandler
 *
 * The exponential backoff gives other transactions time to complete,
 * increasing the chance of a successful retry.
 *
 * IMPORTANT — noRetryFor:
 * Business exceptions (InsufficientBalance, AccountNotFound, DuplicateRequest, etc.)
 * must NOT be retried — they will fail every time. Only transient infrastructure
 * exceptions (OptimisticLockingFailure) should be retried. Without noRetryFor,
 * Spring Retry would try to find a @Recover method for these exceptions
 * and crash with "Cannot locate recovery method" if none matches.
 *
 * IDEMPOTENCY FLOW (for each operation):
 *
 * ┌──────────────────────────────────────────────────────┐
 * │ 1. Check: Does idempotency key exist?                │
 * │    ├── YES → Was the request body the same?          │
 * │    │         ├── YES → Return cached response        │
 * │    │         └── NO  → Throw DuplicateRequestException│
 * │    └── NO  → Process the request                     │
 * │              Save response with idempotency key      │
 * │              Return response                         │
 * └──────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryableTransactionService {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Deposit with idempotency and retry.
     *
     * @Retryable parameters explained:
     * - retryFor: Only retry on OptimisticLockingFailure (concurrency conflict)
     * - noRetryFor: Business exceptions should propagate immediately, never retry
     * - maxAttempts: Try up to 3 times total
     * - backoff: Start at 100ms, multiply by 2 each retry
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            noRetryFor = {
                    DuplicateRequestException.class,
                    InsufficientBalanceException.class,
                    AccountNotFoundException.class,
                    AccountInactiveException.class,
                    InvalidTransactionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
    )
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        log.debug("Attempting deposit | Key: {} | Attempt may be a retry", idempotencyKey);

        // ─── Idempotency Check ───
        String requestJson = toJson(request);
        Optional<TransactionResponse> cached = checkIdempotency(idempotencyKey, requestJson);
        if (cached.isPresent()) {
            return cached.get();
        }

        // ─── Process ───
        TransactionResponse response = transactionService.deposit(request);

        // ─── Cache Response ───
        saveIdempotency(idempotencyKey, requestJson, response);

        return response;
    }

    /**
     * Withdraw with idempotency and retry.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            noRetryFor = {
                    DuplicateRequestException.class,
                    InsufficientBalanceException.class,
                    AccountNotFoundException.class,
                    AccountInactiveException.class,
                    InvalidTransactionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
    )
    public TransactionResponse withdraw(WithdrawRequest request, String idempotencyKey) {
        log.debug("Attempting withdrawal | Key: {} | Attempt may be a retry", idempotencyKey);

        String requestJson = toJson(request);
        Optional<TransactionResponse> cached = checkIdempotency(idempotencyKey, requestJson);
        if (cached.isPresent()) {
            return cached.get();
        }

        TransactionResponse response = transactionService.withdraw(request);
        saveIdempotency(idempotencyKey, requestJson, response);

        return response;
    }

    /**
     * Transfer with idempotency and retry.
     */
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            noRetryFor = {
                    DuplicateRequestException.class,
                    InsufficientBalanceException.class,
                    AccountNotFoundException.class,
                    AccountInactiveException.class,
                    InvalidTransactionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000)
    )
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        log.debug("Attempting transfer | Key: {} | Attempt may be a retry", idempotencyKey);

        String requestJson = toJson(request);
        Optional<TransactionResponse> cached = checkIdempotency(idempotencyKey, requestJson);
        if (cached.isPresent()) {
            return cached.get();
        }

        TransactionResponse response = transactionService.transfer(request);
        saveIdempotency(idempotencyKey, requestJson, response);

        return response;
    }

    // ─── Private Helpers ───

    /**
     * Checks if a request was already processed.
     * If the key exists but the request body differs, it's a client error.
     */
    private Optional<TransactionResponse> checkIdempotency(String idempotencyKey, String requestJson) {
        Optional<IdempotencyRecord> existing = idempotencyService.findExistingRecord(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            String currentHash = idempotencyService.hashRequest(requestJson);

            // Verify the request body matches
            if (!record.getRequestHash().equals(currentHash)) {
                throw new DuplicateRequestException(
                        "Idempotency key '" + idempotencyKey +
                        "' was already used with a different request body");
            }

            log.info("Idempotency key hit — returning cached response | Key: {}", idempotencyKey);

            // Return the cached response
            return Optional.of(fromJson(record.getResponseBody(), TransactionResponse.class));
        }

        return Optional.empty();
    }

    /**
     * Saves the response for future idempotency checks.
     */
    private void saveIdempotency(String idempotencyKey, String requestJson,
                                  TransactionResponse response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.saveRecord(idempotencyKey, requestJson, responseJson, 200);
        } catch (JsonProcessingException e) {
            // Non-fatal: the transaction succeeded, just the idempotency cache failed.
            // The client won't get a cached response on retry, but the duplicate
            // will be caught by the unique constraint on transaction.idempotency_key.
            log.warn("Failed to cache idempotency response | Key: {} | Error: {}",
                    idempotencyKey, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize cached response", e);
        }
    }
}
