package com.banking.system.exception;

import com.banking.system.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralized exception handler for the entire application.
 *
 * @RestControllerAdvice intercepts all exceptions thrown by controllers
 * and converts them into structured JSON error responses.
 *
 * WHY THIS PATTERN?
 * - Consistent error response format across all endpoints
 * - Controllers stay clean (no try-catch blocks)
 * - Logging is centralized
 * - Proper HTTP status codes are always returned
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Business Exception Handlers ───

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        log.warn("Account not found: {} | Path: {}", ex.getMessage(), request.getRequestURI());

        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex, HttpServletRequest request) {
        log.warn("Insufficient balance: {} | Path: {}", ex.getMessage(), request.getRequestURI());

        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Balance", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex, HttpServletRequest request) {
        log.warn("Invalid transaction: {} | Path: {}", ex.getMessage(), request.getRequestURI());

        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Transaction", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountInactive(
            AccountInactiveException ex, HttpServletRequest request) {
        log.warn("Account inactive: {} | Path: {}", ex.getMessage(), request.getRequestURI());

        return buildResponse(HttpStatus.FORBIDDEN, "Account Inactive", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRequest(
            DuplicateRequestException ex, HttpServletRequest request) {
        log.warn("Duplicate request with mismatched body: {} | Path: {}",
                ex.getMessage(), request.getRequestURI());

        return buildResponse(HttpStatus.CONFLICT, "Duplicate Request", ex.getMessage(), request);
    }

    // ─── Infrastructure Exception Handlers ───

    /**
     * Handles optimistic locking failures.
     *
     * This occurs when two concurrent transactions try to update the same account.
     * The retry mechanism should handle most cases, but if all retries are exhausted,
     * this handler returns a 409 Conflict.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.error("Optimistic locking failure after retries exhausted | Path: {} | Detail: {}",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(HttpStatus.CONFLICT, "Concurrent Modification",
                "The account was modified by another transaction. Please retry.", request);
    }

    /**
     * Handles database constraint violations (e.g., unique constraint, check constraint).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation | Path: {} | Detail: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        return buildResponse(HttpStatus.CONFLICT, "Data Integrity Violation",
                "A database constraint was violated. This may be a duplicate operation.", request);
    }

    /**
     * Handles Bean Validation errors (e.g., @NotNull, @Size violations).
     * Collects all field errors into a list for the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.warn("Validation failed | Path: {} | Errors: {}", request.getRequestURI(), details);

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed")
                .details(details)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles missing required headers (e.g., Idempotency-Key).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header: {} | Path: {}", ex.getHeaderName(), request.getRequestURI());

        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Header",
                "Required header '" + ex.getHeaderName() + "' is missing", request);
    }

    /**
     * Catch-all handler for any unexpected exceptions.
     * Returns 500 Internal Server Error and logs the full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error | Path: {} | Exception: {}", request.getRequestURI(), ex.getMessage(), ex);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request);
    }

    // ─── Helper ───

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
