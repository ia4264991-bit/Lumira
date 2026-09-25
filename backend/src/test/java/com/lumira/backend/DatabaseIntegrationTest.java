package com.lumira.backend;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Real PostgreSQL: Connection and query execution succeed")
    void realPostgresConnectionSucceeds() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("Real PostgreSQL: pgcrypto extension is active and gen_random_uuid() works")
    void pgcryptoExtensionWorks() {
        String randomUuidStr = jdbcTemplate.queryForObject("SELECT gen_random_uuid()::text", String.class);
        assertThat(randomUuidStr).isNotNull();
        UUID parsedUuid = UUID.fromString(randomUuidStr);
        assertThat(parsedUuid).isNotNull();
    }

    @Test
    @DisplayName("Real PostgreSQL: Flyway schema_version table records baseline migration")
    void flywayMigrationRecorded() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class
        );
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
