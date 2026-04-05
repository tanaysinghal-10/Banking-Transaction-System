package com.banking.system.repository;

import com.banking.system.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Transaction entities.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find a transaction by its idempotency key.
     * Used to check if a transaction was already processed.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Get all transactions involving a specific account (as source or target).
     * Results are paginated and sorted by creation time (newest first by default).
     *
     * This is a custom JPQL query because Spring Data can't auto-generate
     * an OR condition across two columns from method naming alone.
     */
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accountId OR t.targetAccountId = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}
