package com.lumira.backend;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class LumiraBackendApplicationTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Application context loads successfully against real PostgreSQL")
    void contextLoads() {
        // Verifies Spring context, DataSource connection, and Flyway migration execution against real PostgreSQL
    }

    @Test
    @DisplayName("Health endpoint /v1/health responds UP with service name and timestamp")
    void healthEndpointRespondsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/v1/health"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"service\":\"lumira-backend\"");
    }
}
