package com.banking.system.exception;

/**
 * Thrown when a client reuses an idempotency key with a different request body.
 *
 * This is a client error — the same idempotency key must always be paired
 * with the exact same request. Reusing a key with different parameters could
 * indicate a bug in the client code.
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message) {
        super(message);
    }
}
