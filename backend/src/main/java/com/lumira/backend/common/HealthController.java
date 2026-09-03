package com.lumira.backend.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Minimal, dependency-free health check.
 *
 * <p>Deliberately not using Spring Boot Actuator's {@code /actuator/health}
 * here: that dependency wasn't part of Step 1's agreed scope, and a plain
 * hand-rolled endpoint is enough to prove the skeleton boots and serves
 * requests. Actuator can be added in a later step if real health/metrics
 * monitoring becomes a stated requirement.
 */
@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "lumira-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
