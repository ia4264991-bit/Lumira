package com.lumira.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2 note: since {@code spring-boot-starter-data-jpa} and Flyway are
 * now on the classpath, this test's {@code @SpringBootTest} context will
 * only start successfully against a real, reachable PostgreSQL instance
 * with {@code DB_USER}/{@code DB_PASSWORD} (and optionally
 * {@code DB_HOST}/{@code DB_PORT}/{@code DB_NAME}) set — Spring Boot's JPA
 * autoconfiguration requires a working DataSource, and this project's
 * {@code application.yml} deliberately has no fallback credentials (see
 * that file's comment).
 *
 * <p>Before running {@code mvn test}: {@code docker compose up -d postgres}
 * and export the same env vars docker-compose.yml uses (see
 * {@code .env.example}). No entities exist yet (Step 3), so there is
 * nothing for Hibernate to validate — Flyway running its one baseline
 * migration cleanly is what this test is actually proving.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LumiraBackendApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        // Fails here (not at an assertion) if the datasource is unreachable,
        // credentials are missing, or Flyway's migration doesn't apply
        // cleanly against the configured database.
    }

    @Test
    void healthEndpointRespondsUp() {
        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v1/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
