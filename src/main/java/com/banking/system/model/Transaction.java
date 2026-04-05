package com.banking.system.model;

import com.banking.system.model.enums.TransactionStatus;
import com.banking.system.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a financial transaction.
 *
 * Every financial operation (deposit, withdrawal, transfer) creates a
 * Transaction record. This provides a complete audit trail — a fundamental
 * requirement in financial systems.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. Nullable source/target accounts:
 *    - DEPOSIT:    sourceAccountId = null, targetAccountId = the receiving account
 *    - WITHDRAWAL: sourceAccountId = the debited account, targetAccountId = null
 *    - TRANSFER:   both are set
 *
 * 2. Idempotency key:
 *    Stored on the transaction so we can find already-processed transactions
 *    without hitting the separate idempotency_keys table.
 *
 * 3. Immutable record:
 *    Transactions are append-only. Once created, they are never updated or deleted.
 *    This is a critical invariant in financial systems.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    /**
     * The monetary amount of this transaction.
     * Always positive — the type field indicates the direction.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * The account money is coming FROM.
     * NULL for deposits (money enters the system from outside).
     */
    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    /**
     * The account money is going TO.
     * NULL for withdrawals (money leaves the system).
     */
    @Column(name = "target_account_id")
    private UUID targetAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.SUCCESS;

    /**
     * Client-provided idempotency key.
     * Ensures this exact transaction is processed only once,
     * even if the client retries the request.
     */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
