package com.banking.system.exception;

/**
 * Thrown when an account ID or account number does not exist in the system.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
