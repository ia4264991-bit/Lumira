package com.lumira.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lumira platform backend entry point.
 *
 * <p>Retired old-architecture reference removed 2026-09-12 — this class
 * previously described itself as implementing an academic-hierarchy
 * specification ("LPTS") that has since been retired. See
 * {@code docs/LUMIRA_STATE.md} in the repository root for current status.
 *
 * <p>Functionally unchanged: a Spring Boot application with Postgres/JPA/
 * Flyway/Docker Compose infrastructure and a health check, no domain
 * entities.
 */
@SpringBootApplication
public class LumiraBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiraBackendApplication.class, args);
    }
}
