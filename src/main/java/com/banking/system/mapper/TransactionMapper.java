package com.banking.system.mapper;

import com.banking.system.dto.response.TransactionResponse;
import com.banking.system.model.Transaction;

/**
 * Maps between Transaction entity and TransactionResponse DTO.
 */
public final class TransactionMapper {

    private TransactionMapper() {
        // Utility class — prevent instantiation
    }

    /**
     * Converts a Transaction entity to a TransactionResponse DTO.
     */
    public static TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .status(transaction.getStatus())
                .idempotencyKey(transaction.getIdempotencyKey())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
