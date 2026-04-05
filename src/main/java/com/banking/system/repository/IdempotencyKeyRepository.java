package com.banking.system.repository;

import com.banking.system.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for IdempotencyRecord entities.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find an existing idempotency record by its key.
     * Returns the cached response if the request was already processed.
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Delete all expired idempotency records.
     * Called by a scheduled cleanup job to prevent unbounded table growth.
     *
     * @Modifying tells Spring Data this is a write operation (not a SELECT).
     * The int return value is the number of deleted rows.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord i WHERE i.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}
