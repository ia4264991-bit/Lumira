package com.lumira.backend.user;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseIdentityIntegrationTest extends BaseIntegrationTest {

    @Test
    void firebaseUidIsUniqueAcrossAppUsers() {
        String firebaseUid = "firebase-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app_user (email, display_name, firebase_uid) VALUES (?, ?, ?)",
                "first-" + UUID.randomUUID() + "@example.test", "First", firebaseUid);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO app_user (email, display_name, firebase_uid) VALUES (?, ?, ?)",
                "second-" + UUID.randomUUID() + "@example.test", "Second", firebaseUid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
