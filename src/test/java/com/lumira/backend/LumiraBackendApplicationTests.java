package com.lumira.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1 smoke test: confirms the Spring context loads with no database
 * configured, and that the health endpoint actually responds over HTTP.
 * This is the whole verification bar for a bare skeleton — later steps add
 * real integration tests once there is real behavior to test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LumiraBackendApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        // If the Spring context fails to start (e.g. a misconfigured bean),
        // this test fails before ever reaching an assertion.
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
