package com.banking.system.exception;

/**
 * Thrown when a transaction violates business rules.
 * Examples: transferring to the same account, invalid transaction type.
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
