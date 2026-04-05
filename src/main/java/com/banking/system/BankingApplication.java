package com.banking.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Banking Transaction System.
 *
 * This application demonstrates production-grade backend patterns:
 * - ACID-compliant transactions
 * - Optimistic locking for concurrency control
 * - Idempotency for safe retries
 * - Clean 3-tier architecture
 */
@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
