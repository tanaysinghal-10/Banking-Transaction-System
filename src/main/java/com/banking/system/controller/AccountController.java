package com.banking.system.controller;

import com.banking.system.dto.request.CreateAccountRequest;
import com.banking.system.dto.response.AccountResponse;
import com.banking.system.dto.response.ApiResponse;
import com.banking.system.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for account management.
 *
 * API DESIGN DECISIONS:
 *
 * 1. Versioned URL path (/api/v1/...):
 *    Allows us to introduce breaking changes in /api/v2/ without affecting
 *    existing clients. This is standard practice in production APIs.
 *
 * 2. Consistent response wrapper (ApiResponse):
 *    Every response follows the same structure, making it predictable
 *    for API consumers and easy to parse programmatically.
 *
 * 3. Proper HTTP status codes:
 *    - 201 Created for POST (resource created)
 *    - 200 OK for GET (resource retrieved)
 *    - 4xx for client errors, 5xx for server errors
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    /**
     * POST /api/v1/accounts
     *
     * Creates a new bank account.
     * Returns 201 Created with the account details.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        log.info("REST | Create account request | Holder: {}", request.getHolderName());

        AccountResponse account = accountService.createAccount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", account));
    }

    /**
     * GET /api/v1/accounts/{id}
     *
     * Retrieves account details by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable UUID id) {

        log.debug("REST | Get account by ID: {}", id);

        AccountResponse account = accountService.getAccountById(id);

        return ResponseEntity.ok(ApiResponse.success("Account retrieved successfully", account));
    }

    /**
     * GET /api/v1/accounts/number/{accountNumber}
     *
     * Retrieves account details by human-readable account number.
     */
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @PathVariable String accountNumber) {

        log.debug("REST | Get account by number: {}", accountNumber);

        AccountResponse account = accountService.getAccountByNumber(accountNumber);

        return ResponseEntity.ok(ApiResponse.success("Account retrieved successfully", account));
    }
}
