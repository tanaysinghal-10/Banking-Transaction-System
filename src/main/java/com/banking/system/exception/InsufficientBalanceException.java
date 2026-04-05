package com.banking.system.exception;

/**
 * Thrown when a withdrawal or transfer would result in a negative balance.
 *
 * In a banking system, this is one of the most common business rule violations.
 * The DB also has a CHECK constraint as a safety net, but we fail fast in the
 * service layer to provide better error messages.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
