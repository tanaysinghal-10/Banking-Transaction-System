package com.banking.system.model.enums;

/**
 * Represents the type of a financial transaction.
 *
 * DEPOSIT    — Money coming into an account from an external source.
 * WITHDRAWAL — Money leaving an account to an external destination.
 * TRANSFER   — Money moving between two accounts within the system.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
