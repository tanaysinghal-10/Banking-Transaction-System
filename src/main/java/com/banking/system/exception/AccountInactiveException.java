package com.banking.system.exception;

/**
 * Thrown when an operation is attempted on a non-ACTIVE account.
 * Accounts that are FROZEN or CLOSED cannot process transactions.
 */
public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException(String message) {
        super(message);
    }
}
