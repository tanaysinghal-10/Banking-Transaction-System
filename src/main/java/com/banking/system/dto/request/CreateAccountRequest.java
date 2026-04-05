package com.banking.system.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new bank account.
 * Validation annotations ensure data integrity at the API boundary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    @NotBlank(message = "Holder name is required")
    @Size(min = 2, max = 100, message = "Holder name must be between 2 and 100 characters")
    private String holderName;

    @DecimalMin(value = "0.0", message = "Initial deposit cannot be negative")
    private BigDecimal initialDeposit;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;
}
