package com.banking.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger configuration.
 *
 * Once the application is running, you can access:
 *   - Swagger UI:      http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON:    http://localhost:8080/v3/api-docs
 *   - OpenAPI YAML:    http://localhost:8080/v3/api-docs.yaml
 *
 * TO IMPORT INTO POSTMAN:
 *   1. Open Postman
 *   2. Click "Import" (top-left)
 *   3. Select "Link" tab
 *   4. Paste: http://localhost:8080/v3/api-docs
 *   5. Click "Import"
 *   → All endpoints with request/response schemas are auto-created!
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Transaction System API")
                        .description("Production-grade Banking API demonstrating ACID, optimistic locking, idempotency, and clean architecture.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Banking System")
                                .email("dev@banking.local"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")));
    }
}
