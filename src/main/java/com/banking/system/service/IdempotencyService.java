package com.banking.system.service;

import com.banking.system.model.IdempotencyRecord;
import com.banking.system.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Manages idempotency keys to prevent duplicate transaction processing.
 *
 * IDEMPOTENCY EXPLAINED (SIMPLY):
 *
 * Imagine you're at an ATM and click "Withdraw $100". The ATM processes it,
 * but the screen freezes. Did it work? You press the button again. Without
 * idempotency, you'd lose $200.
 *
 * Idempotency means: "doing the same thing twice has the same effect as
 * doing it once."
 *
 * HOW IT WORKS HERE:
 *
 * 1. Client generates a unique ID (UUID) for each new operation
 * 2. Client sends this ID in the Idempotency-Key header
 * 3. Server checks if this key was already processed
 * 4. If YES → Return the cached response (no duplicate processing)
 * 5. If NO → Process the request, cache the response, return it
 *
 * EDGE CASE — SAME KEY, DIFFERENT REQUEST:
 * If a client reuses a key with a different request body, that's a bug.
 * We detect this by hashing the request body and comparing hashes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    @Value("${app.idempotency.expiry-hours:24}")
    private int expiryHours;

    /**
     * Checks if a request with this idempotency key was already processed.
     *
     * @return The cached IdempotencyRecord if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findExistingRecord(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Saves a processed request's response for future idempotency checks.
     *
     * Uses REQUIRES_NEW propagation so the idempotency record is saved
     * in its own transaction. This ensures the record persists even if
     * the outer transaction has issues.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord(String idempotencyKey, String requestBody,
                           String responseBody, int statusCode) {
        log.debug("Saving idempotency record | Key: {}", idempotencyKey);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hashRequest(requestBody))
                .responseBody(responseBody)
                .statusCode(statusCode)
                .expiresAt(LocalDateTime.now().plusHours(expiryHours))
                .build();

        repository.save(record);
    }

    /**
     * Computes a SHA-256 hash of the request body.
     * Used to detect if a client reuses an idempotency key with different data.
     */
    public String hashRequest(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Scheduled cleanup of expired idempotency records.
     *
     * Runs every hour (configured in application.yml).
     * This prevents the idempotency_keys table from growing unboundedly.
     *
     * WHY CLEANUP?
     * - Storage: Each record takes ~1KB. At 1M requests/day, that's 1GB/day.
     * - Relevance: After 24h, a retry is almost certainly a new intentional request.
     */
    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredRecords() {
        int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records", deleted);
        }
    }
}
