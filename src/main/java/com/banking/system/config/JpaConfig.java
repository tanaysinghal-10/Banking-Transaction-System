package com.banking.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA and scheduling configuration.
 *
 * @EnableJpaAuditing:
 *   Activates Spring Data JPA's auditing feature, supporting
 *   @CreatedDate and @LastModifiedDate annotations on entities.
 *   (We use @PrePersist/@PreUpdate instead for clarity, but this
 *    enables it as a safety net.)
 *
 * @EnableScheduling:
 *   Activates Spring's @Scheduled annotation support.
 *   Used by IdempotencyService to periodically clean up expired keys.
 */
@Configuration
@EnableJpaAuditing
@EnableScheduling
public class JpaConfig {
}
