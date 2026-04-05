package com.banking.system.service;

import com.banking.system.dto.request.CreateAccountRequest;
import com.banking.system.dto.response.AccountResponse;
import com.banking.system.exception.AccountNotFoundException;
import com.banking.system.model.Account;
import com.banking.system.model.enums.AccountStatus;
import com.banking.system.repository.AccountRepository;
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
 * Unit tests for AccountService.
 *
 * These tests use Mockito to mock the repository layer,
 * isolating the service logic from the database.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;

    @BeforeEach
    void setUp() {
        sampleAccount = Account.builder()
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
    }

    @Test
    @DisplayName("Should create account with initial deposit")
    void createAccount_WithInitialDeposit_ShouldSucceed() {
        // Arrange
        CreateAccountRequest request = CreateAccountRequest.builder()
                .holderName("Alice Johnson")
                .initialDeposit(new BigDecimal("5000.00"))
                .currency("USD")
                .build();

        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            return account;
        });

        // Act
        AccountResponse response = accountService.createAccount(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getHolderName()).isEqualTo("Alice Johnson");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getAccountNumber()).startsWith("ACC-");

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should create account with zero balance when no initial deposit")
    void createAccount_WithoutInitialDeposit_ShouldHaveZeroBalance() {
        // Arrange
        CreateAccountRequest request = CreateAccountRequest.builder()
                .holderName("Bob Smith")
                .build();

        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            return account;
        });

        // Act
        AccountResponse response = accountService.createAccount(request);

        // Assert
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCurrency()).isEqualTo("USD"); // Default currency
    }

    @Test
    @DisplayName("Should retrieve account by ID")
    void getAccountById_WhenExists_ShouldReturnAccount() {
        // Arrange
        UUID accountId = sampleAccount.getId();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));

        // Act
        AccountResponse response = accountService.getAccountById(accountId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getHolderName()).isEqualTo("Alice Johnson");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("5000.0000"));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account not found by ID")
    void getAccountById_WhenNotExists_ShouldThrow() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(accountRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.getAccountById(unknownId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Should retrieve account by account number")
    void getAccountByNumber_WhenExists_ShouldReturnAccount() {
        // Arrange
        when(accountRepository.findByAccountNumber("ACC-100001"))
                .thenReturn(Optional.of(sampleAccount));

        // Act
        AccountResponse response = accountService.getAccountByNumber("ACC-100001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("ACC-100001");
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account not found by number")
    void getAccountByNumber_WhenNotExists_ShouldThrow() {
        // Arrange
        when(accountRepository.findByAccountNumber("ACC-999999"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> accountService.getAccountByNumber("ACC-999999"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-999999");
    }
}
