package com.banking.system.model;

import com.banking.system.model.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a bank account.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. OPTIMISTIC LOCKING via @Version:
 *    Hibernate automatically checks the version field on every UPDATE.
 *    If another transaction has already modified the row (incrementing the version),
 *    Hibernate throws OptimisticLockException, preventing lost updates.
 *    This is critical for preventing race conditions on balance updates.
 *
 * 2. BigDecimal for balance:
 *    Financial amounts must NEVER use float/double due to floating-point
 *    precision errors. BigDecimal provides exact decimal arithmetic.
 *    Example: 0.1 + 0.2 == 0.30000000000000004 with double, but 0.3 with BigDecimal.
 *
 * 3. UUID primary key:
 *    Avoids sequential ID guessing attacks and is safe for distributed systems.
 *
 * 4. DB-level CHECK constraint on balance:
 *    Even if application logic has a bug, the database will reject negative balances.
 *    This is defense-in-depth.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable account number (e.g., "ACC-100001").
     * Used for external references instead of exposing UUIDs to users.
     */
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    /**
     * Account balance stored as BigDecimal with 4 decimal places.
     * The precision (19,4) supports up to 999,999,999,999,999.9999
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * OPTIMISTIC LOCKING VERSION FIELD
     *
     * How it works:
     * 1. When an entity is loaded, Hibernate reads the current version (e.g., version=5).
     * 2. When saving, Hibernate generates: UPDATE accounts SET ... WHERE id=? AND version=5
     * 3. If the update affects 0 rows (another TX already changed version to 6),
     *    Hibernate throws OptimisticLockException.
     * 4. The caller can then retry the operation with fresh data.
     *
     * This is preferred over pessimistic locking because:
     * - No database locks are held during business logic execution
     * - Better throughput under low-to-moderate contention
     * - Scales well for read-heavy workloads
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─── Lifecycle Callbacks ───

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
