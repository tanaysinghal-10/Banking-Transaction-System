package com.banking.system.mapper;

import com.banking.system.dto.response.AccountResponse;
import com.banking.system.model.Account;

/**
 * Maps between Account entity and AccountResponse DTO.
 *
 * WHY A MAPPER?
 * - Entities contain internal fields (version, JPA annotations) that clients shouldn't see.
 * - DTOs are the "public contract" — they can evolve independently of the entity.
 * - This separation prevents accidental exposure of internal state.
 *
 * We use simple static methods here instead of MapStruct because:
 * - The project has few entities
 * - Static methods are zero-overhead and easy to understand
 * - No additional dependency needed
 */
public final class AccountMapper {

    private AccountMapper() {
        // Utility class — prevent instantiation
    }

    /**
     * Converts an Account entity to an AccountResponse DTO.
     */
    public static AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .holderName(account.getHolderName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
