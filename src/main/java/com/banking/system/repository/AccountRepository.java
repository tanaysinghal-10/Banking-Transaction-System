package com.banking.system.repository;

import com.banking.system.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Account entities.
 *
 * Spring Data automatically generates the SQL implementation for
 * these method signatures based on naming conventions.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Find an account by its human-readable account number.
     * Used for account lookups in API responses.
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Check if an account number already exists.
     * Used during account creation to ensure uniqueness.
     */
    boolean existsByAccountNumber(String accountNumber);
}
