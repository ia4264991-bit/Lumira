package com.lumira.backend.user;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("B4 — Account deletion and shared Resource ownership")
class AccountDeletionIntegrationTest extends BaseIntegrationTest {
    private UUID deletingUser;
    private UUID spaceOwner;
    private UUID spaceOne;
    private UUID spaceTwo;
    private UUID spaceThree;
    private UUID personalCard;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("TRUNCATE resource_share, resource, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        deletingUser = user("delete-me");
        spaceOwner = user("space-owner");
        personalCard = card(deletingUser, "Personal", false);
        spaceOne = courseSpace("Space one");
        spaceTwo = courseSpace("Space two");
        spaceThree = courseSpace("Space three");
    }

    @Test
    @DisplayName("Private User-owned and Card-owned Resources are deleted with their account and Card")
    void privateResourcesAreDeleted() {
        UUID privateUserResource = resource(deletingUser, null, "private user resource");
        UUID privateCardResource = resource(null, personalCard, "private card resource");

        assertThat(deleteAccount().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(resourceExists(privateUserResource)).isFalse();
        assertThat(resourceExists(privateCardResource)).isFalse();
        assertThat(cardExists(personalCard)).isFalse();
        assertThat(userExists(deletingUser)).isFalse();
    }

    @Test
    @DisplayName("Shared User-owned Resource transfers to oldest active share; inactive and secondary shares are handled deterministically")
    void userOwnedSharedResourceUsesOldestActiveShareAndIdTieBreak() {
        UUID sharedResource = resource(deletingUser, null, "shared user resource");
        UUID inactiveSpace = courseSpace("Inactive older space");
        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID tieWinnerId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID tieLoserId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        share(sharedResource, inactiveSpace, UUID.fromString("00000000-0000-0000-0000-000000000001"), base.minusSeconds(60), false);
        share(sharedResource, spaceOne, olderId, base, true);
        share(sharedResource, spaceTwo, tieWinnerId, base.plusSeconds(5), true);
        share(sharedResource, spaceThree, tieLoserId, base.plusSeconds(5), true);
        // Equal timestamps select the lower immutable share ID, regardless of insertion order.
        jdbcTemplate.update("UPDATE resource_share SET created_at=? WHERE id IN (?,?)", base.plusSeconds(5), tieWinnerId, tieLoserId);
        // Move the nominal oldest share after the tied pair so the tie-break is decisive.
        jdbcTemplate.update("UPDATE resource_share SET created_at=? WHERE id=?", base.plusSeconds(10), olderId);

        assertThat(deleteAccount().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertOwner(sharedResource, spaceTwo);
        assertThat(activeShareCount(sharedResource)).isEqualTo(2);
        assertThat(shareIsActive(sharedResource, spaceTwo)).isTrue();
        assertThat(shareIsActive(sharedResource, spaceThree)).isTrue();
        assertThat(shareIsActive(sharedResource, inactiveSpace)).isFalse();
        assertThat(cardExists(personalCard)).isFalse();
        assertThat(userExists(deletingUser)).isFalse();
    }

    @Test
    @DisplayName("A shared Resource owned by the deleting User's Card survives and one active share selects the Course Space Card")
    void cardOwnedSharedResourceUsesSingleShareCard() {
        UUID sharedResource = resource(null, personalCard, "shared card resource");
        share(sharedResource, spaceThree, UUID.randomUUID(), Instant.parse("2026-02-01T00:00:00Z"), true);

        assertThat(deleteAccount().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertOwner(sharedResource, spaceThree);
        assertThat(cardExists(personalCard)).isFalse();
        assertThat(userExists(deletingUser)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT num_nonnulls(owner_card_id,owner_user_id) FROM resource WHERE id=?", Integer.class, sharedResource)).isEqualTo(1);
    }

    @Test
    @DisplayName("Account deletion is rejected atomically while the User owns an active Course Space")
    void soleOwnerCourseSpaceBlocksDeletion() {
        UUID ownCourseSpace = courseSpaceOwnedBy(deletingUser, "My Course Space");
        UUID privateResource = resource(deletingUser, null, "still private");

        ResponseEntity<Map> response = deleteAccount();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userExists(deletingUser)).isTrue();
        assertThat(cardExists(personalCard)).isTrue();
        assertThat(cardExists(ownCourseSpace)).isTrue();
        assertThat(resourceExists(privateResource)).isTrue();
    }

    private ResponseEntity<Map> deleteAccount() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(deletingUser.toString());
        return restTemplate.exchange(url("/v1/me"), HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    private UUID courseSpace(String name) { return courseSpaceOwnedBy(spaceOwner, name); }

    private UUID courseSpaceOwnedBy(UUID owner, String name) {
        UUID id = card(owner, name, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(owner.toString());
        ResponseEntity<Map> response = restTemplate.exchange(url("/v1/cards/" + id + "/share"), HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private void share(UUID resourceId, UUID cardId, UUID shareId, Instant createdAt, boolean active) {
        jdbcTemplate.update("INSERT INTO resource_share(id,resource_id,card_id,active,created_at) VALUES (?,?,?,?,?)",
                shareId, resourceId, cardId, active, createdAt);
    }

    private UUID resource(UUID ownerUserId, UUID ownerCardId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO resource(id,owner_user_id,owner_card_id,title,original_filename,mime_type,file_size_bytes,processing_status,original_bytes,extracted_content,image_metadata) " +
                        "VALUES (?,?,?,?,?,'text/plain',0,'READY',decode('78','hex'),'[]'::jsonb,'{}'::jsonb)",
                id, ownerUserId, ownerCardId, title, title + ".txt");
        return id;
    }

    private UUID user(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user(id,email,display_name) VALUES (?,?,?)", id, suffix + "@example.com", suffix);
        return id;
    }

    private UUID card(UUID ownerId, String name, boolean shared) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,?,0,false)",
                id, ownerId, name, shared);
        return id;
    }

    private void assertOwner(UUID resourceId, UUID expectedCard) {
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM resource WHERE id=?", UUID.class, resourceId)).isEqualTo(expectedCard);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, resourceId)).isNull();
    }

    private boolean resourceExists(UUID id) { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM resource WHERE id=?)", Boolean.class, id)); }
    private boolean cardExists(UUID id) { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM card WHERE id=?)", Boolean.class, id)); }
    private boolean userExists(UUID id) { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=?)", Boolean.class, id)); }
    private int activeShareCount(UUID resourceId) { return jdbcTemplate.queryForObject("SELECT count(*) FROM resource_share WHERE resource_id=? AND active", Integer.class, resourceId); }
    private boolean shareIsActive(UUID resourceId, UUID cardId) { return jdbcTemplate.queryForObject("SELECT active FROM resource_share WHERE resource_id=? AND card_id=?", Boolean.class, resourceId, cardId); }
}
