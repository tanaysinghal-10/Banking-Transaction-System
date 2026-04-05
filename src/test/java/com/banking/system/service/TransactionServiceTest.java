package com.banking.system.service;

import com.banking.system.dto.request.DepositRequest;
import com.banking.system.dto.request.TransferRequest;
import com.banking.system.dto.request.WithdrawRequest;
import com.banking.system.dto.response.TransactionResponse;
import com.banking.system.exception.AccountNotFoundException;
import com.banking.system.exception.InsufficientBalanceException;
import com.banking.system.exception.InvalidTransactionException;
import com.banking.system.model.Account;
import com.banking.system.model.Transaction;
import com.banking.system.model.enums.AccountStatus;
import com.banking.system.model.enums.TransactionStatus;
import com.banking.system.model.enums.TransactionType;
import com.banking.system.repository.AccountRepository;
import com.banking.system.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService.
 *
 * Tests cover:
 * - Successful deposit, withdrawal, transfer
 * - Insufficient balance
 * - Account not found
 * - Self-transfer prevention
 * - Account status validation
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        aliceAccount = Account.builder()
                .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
                .accountNumber("ACC-100001")
                .holderName("Alice Johnson")
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        bobAccount = Account.builder()
                .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
                .accountNumber("ACC-100002")
                .holderName("Bob Smith")
                .balance(new BigDecimal("3000.0000"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── DEPOSIT TESTS ───

    @Test
    @DisplayName("Should successfully deposit money")
    void deposit_ShouldIncreaseBalance() {
        // Arrange
        DepositRequest request = DepositRequest.builder()
                .accountId(aliceAccount.getId())
                .amount(new BigDecimal("1000.00"))
                .description("Salary deposit")
                .build();

        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(aliceAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(LocalDateTime.now());
            return tx;
        });

        // Act
        TransactionResponse response = transactionService.deposit(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        // Verify balance was updated
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("6000.0000"));
    }

    @Test
    @DisplayName("Should fail deposit on non-existent account")
    void deposit_WhenAccountNotFound_ShouldThrow() {
        UUID unknownId = UUID.randomUUID();
        DepositRequest request = DepositRequest.builder()
                .accountId(unknownId)
                .amount(new BigDecimal("100.00"))
                .build();

        when(accountRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deposit(request))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ─── WITHDRAWAL TESTS ───

    @Test
    @DisplayName("Should successfully withdraw money")
    void withdraw_WhenSufficientBalance_ShouldSucceed() {
        // Arrange
        WithdrawRequest request = WithdrawRequest.builder()
                .accountId(aliceAccount.getId())
                .amount(new BigDecimal("1000.00"))
                .build();

        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(aliceAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(LocalDateTime.now());
            return tx;
        });

        // Act
        TransactionResponse response = transactionService.withdraw(request);

        // Assert
        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("4000.0000"));
    }

    @Test
    @DisplayName("Should fail withdrawal when insufficient balance")
    void withdraw_WhenInsufficientBalance_ShouldThrow() {
        // Arrange
        WithdrawRequest request = WithdrawRequest.builder()
                .accountId(aliceAccount.getId())
                .amount(new BigDecimal("10000.00")) // More than the 5000 balance
                .build();

        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));

        // Act & Assert
        assertThatThrownBy(() -> transactionService.withdraw(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ─── TRANSFER TESTS ───

    @Test
    @DisplayName("Should successfully transfer money between accounts")
    void transfer_ShouldDebitSourceAndCreditTarget() {
        // Arrange
        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(aliceAccount.getId())
                .targetAccountId(bobAccount.getId())
                .amount(new BigDecimal("1500.00"))
                .description("Payment for services")
                .build();

        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(bobAccount.getId())).thenReturn(Optional.of(bobAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(LocalDateTime.now());
            return tx;
        });

        // Act
        TransactionResponse response = transactionService.transfer(request);

        // Assert
        assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // Verify balances
        assertThat(aliceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("3500.0000"));
        assertThat(bobAccount.getBalance()).isEqualByComparingTo(new BigDecimal("4500.0000"));

        // Verify account was saved twice (source and target)
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    @DisplayName("Should fail transfer when source has insufficient balance")
    void transfer_WhenInsufficientBalance_ShouldThrow() {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(aliceAccount.getId())
                .targetAccountId(bobAccount.getId())
                .amount(new BigDecimal("999999.00"))
                .build();

        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findById(bobAccount.getId())).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("Should fail transfer to the same account")
    void transfer_WhenSameAccount_ShouldThrow() {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(aliceAccount.getId())
                .targetAccountId(aliceAccount.getId()) // Same account
                .amount(new BigDecimal("100.00"))
                .build();

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("same account");
    }

    @Test
    @DisplayName("Should fail transfer when source account not found")
    void transfer_WhenSourceNotFound_ShouldThrow() {
        UUID unknownId = UUID.randomUUID();
        TransferRequest request = TransferRequest.builder()
                .sourceAccountId(unknownId)
                .targetAccountId(bobAccount.getId())
                .amount(new BigDecimal("100.00"))
                .build();

        // The transfer loads accounts in sorted UUID order, so which one is loaded first depends
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
