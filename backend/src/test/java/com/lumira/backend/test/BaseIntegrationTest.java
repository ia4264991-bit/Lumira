package com.lumira.backend.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for backend integration tests running against real PostgreSQL.
 *
 * <p>Provides:
 * <ul>
 *   <li>Full Spring Boot context with a random server port.</li>
 *   <li>Real PostgreSQL database connection and Flyway schema execution.</li>
 *   <li>Pre-configured {@link TestRestTemplate} and {@link JdbcTemplate}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Helper to construct full URL for an endpoint path.
     */
    protected String url(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://localhost:" + port + path;
    }
}
