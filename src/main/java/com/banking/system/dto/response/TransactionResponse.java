package com.banking.system.dto.response;

import com.banking.system.model.enums.TransactionStatus;
import com.banking.system.model.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for transaction data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private UUID id;
    private TransactionType type;
    private BigDecimal amount;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private TransactionStatus status;
    private String idempotencyKey;
    private String description;
    private LocalDateTime createdAt;
}
