package com.banking.system.model.enums;

/**
 * Represents the lifecycle state of a bank account.
 *
 * ACTIVE — Account is operational and can process transactions.
 * FROZEN — Account is temporarily restricted (e.g., fraud investigation).
 * CLOSED — Account is permanently closed. No transactions allowed.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
