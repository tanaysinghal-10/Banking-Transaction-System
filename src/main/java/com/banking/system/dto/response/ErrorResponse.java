package com.banking.system.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured error response returned by the GlobalExceptionHandler.
 *
 * Example:
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "details": ["Holder name is required", "Amount must be greater than 0"],
 *   "path": "/api/v1/accounts",
 *   "timestamp": "2024-01-15T10:30:00"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private List<String> details;
    private String path;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
