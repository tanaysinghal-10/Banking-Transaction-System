package com.banking.system.model.enums;

/**
 * Represents the outcome of a transaction.
 *
 * SUCCESS — Transaction completed successfully.
 * FAILED  — Transaction failed (e.g., insufficient balance, validation error).
 * PENDING — Transaction is in progress (used during multi-step operations).
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING
}
