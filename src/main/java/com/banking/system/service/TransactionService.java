package com.banking.system.service;

import com.banking.system.dto.request.DepositRequest;
import com.banking.system.dto.request.TransferRequest;
import com.banking.system.dto.request.WithdrawRequest;
import com.banking.system.dto.response.TransactionResponse;
import com.banking.system.exception.*;
import com.banking.system.mapper.TransactionMapper;
import com.banking.system.model.Account;
import com.banking.system.model.Transaction;
import com.banking.system.model.enums.AccountStatus;
import com.banking.system.model.enums.TransactionStatus;
import com.banking.system.model.enums.TransactionType;
import com.banking.system.repository.AccountRepository;
import com.banking.system.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core service for all financial transaction operations.
 *
 * TRANSACTION FLOW (Transfer Example):
 *
 * 1. Controller receives POST /api/v1/transactions/transfer with Idempotency-Key header.
 * 2. Controller delegates to RetryableTransactionService (which wraps this service with retry logic).
 * 3. IdempotencyService checks if this key was already processed → return cached response if yes.
 * 4. This service:
 *    a. Loads both accounts from the database
 *    b. Validates both are ACTIVE
 *    c. Checks source has sufficient balance
 *    d. DEBIT source account (balance -= amount)
 *    e. CREDIT target account (balance += amount)
 *    f. Create Transaction record
 *    g. Save all changes within a single @Transactional boundary
 * 5. If step (g) encounters an OptimisticLockException (another TX modified the account),
 *    the entire transaction is rolled back, and Spring Retry re-executes from step 3.
 * 6. If successful, the response is cached with the idempotency key.
 *
 * ATOMICITY:
 * All database operations within a @Transactional method either ALL succeed or ALL roll back.
 * There's no state where one account is debited but the other isn't credited.
 *
 * CONCURRENCY SAFETY:
 * The @Version field on Account means that if two threads read the same account balance
 * and both try to update it, only one will succeed. The other will get an
 * OptimisticLockException and retry with the updated balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Deposits money into an account.
     *
     * This is the simplest transaction type:
     * - No source account (money comes from outside the system)
     * - No balance check needed (you can always add money)
     * - Only the target account's version needs to be checked
     */
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        log.info("Processing deposit | Account: {} | Amount: {}",
                request.getAccountId(), request.getAmount());

        // Load and validate the account
        Account account = loadAndValidateAccount(request.getAccountId());

        // Credit the account
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Record the transaction
        Transaction transaction = Transaction.builder()
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .targetAccountId(account.getId())
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : "Deposit of " + request.getAmount())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Deposit successful | TX: {} | Account: {} | Amount: {} | New Balance: {}",
                saved.getId(), account.getId(), request.getAmount(), account.getBalance());

        return TransactionMapper.toResponse(saved);
    }

    /**
     * Withdraws money from an account.
     *
     * Critical check: balance must be >= withdrawal amount.
     * We check in the application layer AND the DB has a CHECK constraint.
     * Defense-in-depth: even if our Java code has a bug, the DB won't allow it.
     */
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request) {
        log.info("Processing withdrawal | Account: {} | Amount: {}",
                request.getAccountId(), request.getAmount());

        // Load and validate the account
        Account account = loadAndValidateAccount(request.getAccountId());

        // Check sufficient balance
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance | Account: {} | Balance: {} | Requested: {}",
                    account.getId(), account.getBalance(), request.getAmount());
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            account.getBalance(), request.getAmount()));
        }

        // Debit the account
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        // Record the transaction
        Transaction transaction = Transaction.builder()
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .sourceAccountId(account.getId())
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : "Withdrawal of " + request.getAmount())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Withdrawal successful | TX: {} | Account: {} | Amount: {} | New Balance: {}",
                saved.getId(), account.getId(), request.getAmount(), account.getBalance());

        return TransactionMapper.toResponse(saved);
    }

    /**
     * Transfers money between two accounts.
     *
     * DEADLOCK PREVENTION:
     * If Thread A transfers from Account 1→2, and Thread B transfers from Account 2→1,
     * without ordering, they could deadlock:
     *   Thread A locks Account 1, waits for Account 2
     *   Thread B locks Account 2, waits for Account 1
     *
     * Solution: Always process accounts in sorted UUID order.
     * Both threads will lock Account 1 first, then Account 2.
     * One thread will wait, but no deadlock occurs.
     *
     * WHY compareTo FOR UUID ORDERING?
     * UUID implements Comparable, so sorting is deterministic and consistent.
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Processing transfer | From: {} | To: {} | Amount: {}",
                request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount());

        // Validate: cannot transfer to yourself
        if (request.getSourceAccountId().equals(request.getTargetAccountId())) {
            throw new InvalidTransactionException("Cannot transfer to the same account");
        }

        // ─── DEADLOCK PREVENTION: Load accounts in consistent order ───
        UUID firstId, secondId;
        if (request.getSourceAccountId().compareTo(request.getTargetAccountId()) < 0) {
            firstId = request.getSourceAccountId();
            secondId = request.getTargetAccountId();
        } else {
            firstId = request.getTargetAccountId();
            secondId = request.getSourceAccountId();
        }

        // Load both accounts (in sorted order)
        Account firstAccount = loadAndValidateAccount(firstId);
        Account secondAccount = loadAndValidateAccount(secondId);

        // Identify source and target from the loaded accounts
        Account source = firstId.equals(request.getSourceAccountId()) ? firstAccount : secondAccount;
        Account target = firstId.equals(request.getTargetAccountId()) ? firstAccount : secondAccount;

        // Check sufficient balance on source
        if (source.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance for transfer | Source: {} | Balance: {} | Requested: {}",
                    source.getId(), source.getBalance(), request.getAmount());
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            source.getBalance(), request.getAmount()));
        }

        // ─── PERFORM THE TRANSFER ───
        // Both operations happen within the same @Transactional boundary.
        // If either fails, BOTH roll back. There's no state where money
        // is debited from source but not credited to target.

        source.setBalance(source.getBalance().subtract(request.getAmount()));
        target.setBalance(target.getBalance().add(request.getAmount()));

        accountRepository.save(source);
        accountRepository.save(target);

        // Record the transaction
        Transaction transaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .sourceAccountId(source.getId())
                .targetAccountId(target.getId())
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : "Transfer of " + request.getAmount() + " from " +
                          source.getAccountNumber() + " to " + target.getAccountNumber())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Transfer successful | TX: {} | From: {} ({}) | To: {} ({}) | Amount: {}",
                saved.getId(),
                source.getId(), source.getBalance(),
                target.getId(), target.getBalance(),
                request.getAmount());

        return TransactionMapper.toResponse(saved);
    }

    /**
     * Retrieves paginated transaction history for an account.
     * Shows all transactions where the account is either source or target.
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(UUID accountId, Pageable pageable) {
        log.debug("Fetching transaction history | Account: {} | Page: {}",
                accountId, pageable.getPageNumber());

        // Verify the account exists
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException("Account with ID '" + accountId + "' not found");
        }

        return transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionMapper::toResponse);
    }

    // ─── Private Helpers ───

    /**
     * Loads an account and validates it is ACTIVE.
     * This is called before every financial operation.
     */
    private Account loadAndValidateAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account with ID '" + accountId + "' not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(
                    "Account " + account.getAccountNumber() + " is " + account.getStatus() +
                    ". Only ACTIVE accounts can process transactions.");
        }

        return account;
    }
}
