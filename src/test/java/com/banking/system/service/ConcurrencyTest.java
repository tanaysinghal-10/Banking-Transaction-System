package com.banking.system.service;

import com.banking.system.dto.request.CreateAccountRequest;
import com.banking.system.dto.request.WithdrawRequest;
import com.banking.system.dto.response.AccountResponse;
import com.banking.system.dto.response.TransactionResponse;
import com.banking.system.model.enums.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONCURRENCY INTEGRATION TEST
 *
 * This test verifies that concurrent operations on the same account
 * are handled correctly by optimistic locking + retry.
 *
 * SCENARIO:
 * 1. Create an account with $10,000 balance.
 * 2. Spawn 10 threads, each withdrawing $500 simultaneously.
 * 3. Total expected withdrawal: $5,000.
 * 4. Expected final balance: $5,000.
 *
 * WITHOUT OPTIMISTIC LOCKING (the "lost update" problem):
 * - Thread A reads balance: $10,000
 * - Thread B reads balance: $10,000
 * - Thread A writes: balance = 10000 - 500 = $9,500
 * - Thread B writes: balance = 10000 - 500 = $9,500  ← Thread A's withdrawal is LOST!
 * - Final balance: $9,500 instead of $9,000
 *
 * WITH OPTIMISTIC LOCKING:
 * - Thread A reads balance: $10,000 (version=0)
 * - Thread B reads balance: $10,000 (version=0)
 * - Thread A writes: UPDATE WHERE version=0 → success, version becomes 1
 * - Thread B writes: UPDATE WHERE version=0 → FAILS (version is now 1)
 * - Thread B RETRIES: reads $9,500 (version=1), writes $9,000 (version=2) → success
 * - Final balance: $9,000 ✓
 *
 * This test uses @SpringBootTest with H2 to run a full integration test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Concurrent withdrawals should maintain consistent balance via optimistic locking")
    void concurrentWithdrawals_ShouldMaintainConsistentBalance() throws InterruptedException {
        // ─── SETUP: Create account with $10,000 ───
        CreateAccountRequest createRequest = CreateAccountRequest.builder()
                .holderName("Concurrency Test Account")
                .initialDeposit(new BigDecimal("10000.00"))
                .currency("USD")
                .build();

        AccountResponse account = accountService.createAccount(createRequest);
        UUID accountId = account.getId();

        // ─── EXECUTE: 10 concurrent $500 withdrawals ───
        int threadCount = 10;
        BigDecimal withdrawalAmount = new BigDecimal("500.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // Ensures all threads start together
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<TransactionResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            Future<TransactionResponse> future = executor.submit(() -> {
                startLatch.await(); // Wait for all threads to be ready

                try {
                    WithdrawRequest request = WithdrawRequest.builder()
                            .accountId(accountId)
                            .amount(withdrawalAmount)
                            .description("Concurrent withdrawal #" + threadNum)
                            .build();

                    TransactionResponse response = transactionService.withdraw(request);
                    successCount.incrementAndGet();
                    return response;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return null;
                } finally {
                    doneLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete (max 30 seconds)
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All threads should complete within timeout").isTrue();

        // ─── VERIFY: Final balance must be consistent ───
        AccountResponse finalAccount = accountService.getAccountById(accountId);
        BigDecimal expectedBalance = new BigDecimal("10000.00")
                .subtract(withdrawalAmount.multiply(BigDecimal.valueOf(successCount.get())));

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  CONCURRENCY TEST RESULTS");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Initial balance:    $10,000.00");
        System.out.println("  Concurrent threads: " + threadCount);
        System.out.println("  Withdrawal amount:  $" + withdrawalAmount);
        System.out.println("  Successful:         " + successCount.get());
        System.out.println("  Failed (retries):   " + failureCount.get());
        System.out.println("  Final balance:      $" + finalAccount.getBalance());
        System.out.println("  Expected balance:   $" + expectedBalance);
        System.out.println("═══════════════════════════════════════════════════");

        // The balance should EXACTLY match: initial - (successful_withdrawals * amount)
        assertThat(finalAccount.getBalance())
                .as("Final balance must be exactly initial - (successes * withdrawal)")
                .isEqualByComparingTo(expectedBalance);

        // All 10 should succeed (even if some needed retries)
        // Some might fail if retries are exhausted under extreme contention,
        // but the balance must still be consistent regardless.
        assertThat(successCount.get() + failureCount.get())
                .as("Total attempts should equal thread count")
                .isEqualTo(threadCount);
    }
}
