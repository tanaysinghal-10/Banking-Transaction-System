package com.banking.system.service;

import com.banking.system.dto.request.CreateAccountRequest;
import com.banking.system.dto.response.AccountResponse;
import com.banking.system.exception.AccountNotFoundException;
import com.banking.system.mapper.AccountMapper;
import com.banking.system.model.Account;
import com.banking.system.model.enums.AccountStatus;
import com.banking.system.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service layer for account management operations.
 *
 * All methods are annotated with @Transactional to ensure ACID properties.
 * Read-only methods use @Transactional(readOnly = true) which:
 * - Tells Hibernate not to track dirty state (performance optimization)
 * - Some JDBC drivers can optimize read-only connections
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    /**
     * Creates a new bank account.
     *
     * Flow:
     * 1. Generate a unique account number
     * 2. Build the Account entity with defaults
     * 3. Persist to database
     * 4. Return the DTO (never expose the entity directly)
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for holder: {}", request.getHolderName());

        // Generate a unique account number
        String accountNumber = generateAccountNumber();

        // Determine initial balance (default 0 if not specified)
        BigDecimal initialBalance = request.getInitialDeposit() != null
                ? request.getInitialDeposit()
                : BigDecimal.ZERO;

        // Determine currency (default USD)
        String currency = request.getCurrency() != null ? request.getCurrency() : "USD";

        // Build and save the account
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .holderName(request.getHolderName())
                .balance(initialBalance)
                .currency(currency)
                .status(AccountStatus.ACTIVE)
                .build();

        Account savedAccount = accountRepository.save(account);

        log.info("Account created successfully | ID: {} | Number: {} | Holder: {}",
                savedAccount.getId(), savedAccount.getAccountNumber(), savedAccount.getHolderName());

        return AccountMapper.toResponse(savedAccount);
    }

    /**
     * Retrieves an account by its UUID.
     *
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        log.debug("Fetching account by ID: {}", accountId);

        Account account = findAccountOrThrow(accountId);
        return AccountMapper.toResponse(account);
    }

    /**
     * Retrieves an account by its human-readable account number.
     *
     * @throws AccountNotFoundException if the account doesn't exist
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        log.debug("Fetching account by number: {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with number '" + accountNumber + "' not found"));

        return AccountMapper.toResponse(account);
    }

    /**
     * Internal method to fetch an account entity by ID.
     * Used by TransactionService which needs the raw entity for balance updates.
     */
    @Transactional(readOnly = true)
    public Account findAccountEntityById(UUID accountId) {
        return findAccountOrThrow(accountId);
    }

    // ─── Private Helpers ───

    private Account findAccountOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with ID '" + accountId + "' not found"));
    }

    /**
     * Generates a unique account number in the format ACC-XXXXXX.
     * Uses a random 6-digit number and checks for collisions.
     *
     * In a real system, you'd use a sequence or a more sophisticated
     * algorithm, but this is simple and sufficient for demonstration.
     */
    private String generateAccountNumber() {
        String accountNumber;
        do {
            int number = ThreadLocalRandom.current().nextInt(100000, 999999);
            accountNumber = "ACC-" + number;
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }
}
