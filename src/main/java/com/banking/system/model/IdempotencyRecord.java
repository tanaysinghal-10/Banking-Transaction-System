package com.banking.system.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores processed request/response pairs keyed by an idempotency key.
 *
 * IDEMPOTENCY FLOW:
 *
 * 1. Client sends a request with header: Idempotency-Key: <unique-key>
 * 2. Server checks this table for an existing record with that key.
 * 3. IF FOUND → Return the cached response (no re-processing).
 * 4. IF NOT FOUND → Process the request, store the response here, return it.
 *
 * WHY THIS MATTERS:
 *
 * In financial systems, network issues can cause clients to retry requests.
 * Without idempotency, a double-click on "Transfer $500" could move $1000.
 * The idempotency key guarantees exactly-once processing semantics.
 *
 * EXPIRY:
 * Records expire after 24 hours (configurable). A scheduled job cleans them up.
 * We don't keep them forever because:
 * - Storage grows indefinitely
 * - After 24h, if a client retries, it's likely a new intentional request
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The unique key provided by the client.
     * Typically a UUID generated client-side.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * SHA-256 hash of the request body.
     * Used to detect if a client reuses the same idempotency key
     * for a DIFFERENT request (which is an error).
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /**
     * The JSON-serialized response that was returned for this request.
     * This is replayed verbatim on duplicate requests.
     */
    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    /**
     * The HTTP status code of the original response.
     */
    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this record should be cleaned up.
     * After expiry, the idempotency key can be reused.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
