package com.banking.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry across the application.
 *
 * WHAT IS SPRING RETRY?
 * Spring Retry provides declarative retry support using annotations.
 * When a method annotated with @Retryable throws a specified exception,
 * Spring automatically re-invokes the method with configurable:
 * - Max attempts (default: 3)
 * - Backoff strategy (fixed, exponential, random)
 * - Recovery method (fallback when all retries are exhausted)
 *
 * WHY WE NEED IT:
 * OptimisticLockException is a transient failure — it means another
 * transaction modified the same row. On retry, we reload the fresh
 * data and try again. Most retries succeed on the 2nd or 3rd attempt.
 *
 * Without retry, every concurrent conflict would result in a 409 error
 * to the client, which is a poor user experience.
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // @EnableRetry activates the @Retryable annotations in service classes.
    // Retry parameters are configured on each @Retryable annotation.
}
