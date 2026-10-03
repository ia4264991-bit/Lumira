package com.lumira.backend.resource;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("B4 â€” Resource sharing and authorization")
class ResourceSharingIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID admin;
    private UUID member;
    private UUID outsider;
    private UUID courseSpace;
    private UUID secondCourseSpace;
    private UUID resource;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("TRUNCATE artifact_share, resource, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("share-owner");
        admin = user("share-admin");
        member = user("share-member");
        outsider = user("share-outsider");
        courseSpace = card(owner, "Space one", false);
        secondCourseSpace = card(owner, "Space two", false);
        enableSpace(courseSpace, owner);
        enableSpace(secondCourseSpace, owner);
        addMembership(courseSpace, admin, card(admin, "Admin personal", false), "ADMIN");
        addMembership(courseSpace, member, card(member, "Member personal", false), "MEMBER");
        resource = resource(owner, null, "Shared material");
    }

    @Test
    @DisplayName("Owner sharing grants current members access and unshare revokes it without deleting or transferring ownership")
    void shareAccessAndOrdinaryUnshare() {
        assertThat(share(owner, courseSpace).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(share(owner, courseSpace).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE resource_id=? AND card_id=?",
                Integer.class, resource, courseSpace)).isEqualTo(1);
        assertThat(eventCount(courseSpace, "ARTIFACT_SHARED")).isEqualTo(1);
        assertThat(read(member).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read(outsider).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        jdbcTemplate.update("UPDATE card_membership SET status='REMOVED' WHERE card_id=? AND user_id=?", courseSpace, member);
        assertThat(read(member).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        jdbcTemplate.update("UPDATE card_membership SET status='ACTIVE' WHERE card_id=? AND user_id=?", courseSpace, member);

        assertThat(request(HttpMethod.DELETE, owner,
                "/v1/artifacts/resource/" + resource + "/share/" + courseSpace, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(read(member).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM resource WHERE id=?", Integer.class, resource)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, resource)).isEqualTo(owner);
        assertThat(eventCount(courseSpace, "ARTIFACT_SHARED")).isEqualTo(1);
        assertThat(eventCount(courseSpace, "CONTENT_UNSHARED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Owner force-unshare allows a null reason while preserving the Resource and unrelated share")
    void ownerForceUnshareIsReasonOptionalAndScopedToOneSpace() {
        share(owner, courseSpace);
        share(owner, secondCourseSpace);

        ResponseEntity<Map> result = force(owner, courseSpace, null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeShare(courseSpace)).isFalse();
        assertThat(activeShare(secondCourseSpace)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM resource WHERE id=?", Integer.class, resource)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, resource)).isEqualTo(owner);
        assertThat(eventCount(courseSpace, "CONTENT_FORCE_UNSHARED")).isEqualTo(1);
        String payload = jdbcTemplate.queryForObject("SELECT payload::text FROM course_space_event WHERE card_id=? AND type='CONTENT_FORCE_UNSHARED'", String.class, courseSpace);
        assertThat(payload).contains("\"reason\": null");
    }

    @Test
    @DisplayName("Owner may also supply a structured reason for force-unshare")
    void ownerForceUnshareAcceptsReason() {
        share(owner, courseSpace);
        assertThat(force(owner, courseSpace, Map.of("reason", "SAFETY")).getStatusCode()).isEqualTo(HttpStatus.OK);
        String payload = jdbcTemplate.queryForObject("SELECT payload::text FROM course_space_event WHERE card_id=? AND type='CONTENT_FORCE_UNSHARED'", String.class, courseSpace);
        assertThat(payload).contains("\"reason\": \"SAFETY\"");
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, resource)).isEqualTo(owner);
    }

    @Test
    @DisplayName("Admin force-unshare requires a valid structured reason and emits the same force event")
    void adminForceUnshareRequiresStructuredReason() {
        share(owner, courseSpace);
        assertThat(force(admin, courseSpace, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(force(admin, courseSpace, Map.of("reason", "NOT_A_REASON")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(activeShare(courseSpace)).isTrue();
        assertThat(eventCount(courseSpace, "CONTENT_FORCE_UNSHARED")).isZero();

        assertThat(force(admin, courseSpace, Map.of("reason", "PRIVACY", "note", "Private information"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeShare(courseSpace)).isFalse();
        assertThat(eventCount(courseSpace, "CONTENT_FORCE_UNSHARED")).isEqualTo(1);
        String payload = jdbcTemplate.queryForObject("SELECT payload::text FROM course_space_event WHERE card_id=? AND type='CONTENT_FORCE_UNSHARED'", String.class, courseSpace);
        assertThat(payload).contains("\"reason\": \"PRIVACY\"", "\"note\": \"Private information\"");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM resource WHERE id=?", Integer.class, resource)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, resource)).isEqualTo(owner);
    }

    @Test
    @DisplayName("Artifact owner cannot force-unshare without Course Space authority and failed operations emit no event")
    void forceUnshareRequiresSpaceAuthority() {
        share(owner, courseSpace);
        assertThat(force(member, courseSpace, Map.of("reason", "SAFETY")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(force(outsider, courseSpace, Map.of("reason", "SAFETY")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(activeShare(courseSpace)).isTrue();
        assertThat(eventCount(courseSpace, "CONTENT_FORCE_UNSHARED")).isZero();
    }

    @Test
    @DisplayName("Owner may supply a structured reason and a sharer's departure does not remove a share")
    void ownerReasonIsOptionalAndContributorDeparturePreservesShare() {
        assertThat(force(owner, courseSpace, Map.of("reason", "SAFETY")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        share(owner, courseSpace);
        assertThat(force(owner, courseSpace, Map.of("reason", "SAFETY")).getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID contributedResource = resource(member, null, "contributed material");
        assertThat(request(HttpMethod.POST, member, "/v1/artifacts/resource/" + contributedResource + "/share",
                Map.of("cardId", courseSpace), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbcTemplate.update("UPDATE card_membership SET status='LEFT' WHERE card_id=? AND user_id=?", courseSpace, member);
        assertThat(request(HttpMethod.GET, owner, "/v1/resources/" + contributedResource, null, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM artifact_share WHERE resource_id=? AND card_id=?",
                Boolean.class, contributedResource, courseSpace)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM resource WHERE id=?", UUID.class, contributedResource)).isEqualTo(member);
    }

    private ResponseEntity<Map> share(UUID actor, UUID cardId) {
        return request(HttpMethod.POST, actor, "/v1/artifacts/resource/" + resource + "/share",
                Map.of("cardId", cardId), Map.class);
    }

    private ResponseEntity<Map> force(UUID actor, UUID cardId, Object body) {
        return request(HttpMethod.POST, actor, "/v1/artifacts/resource/" + resource + "/force-unshare/" + cardId,
                body, Map.class);
    }

    private ResponseEntity<byte[]> read(UUID actor) {
        return request(HttpMethod.GET, actor, "/v1/resources/" + resource, null, byte[].class);
    }

    private <T> ResponseEntity<T> request(HttpMethod method, UUID actor, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private int eventCount(UUID cardId, String type) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id=? AND type=?", Integer.class, cardId, type);
    }

    private boolean activeShare(UUID cardId) {
        return jdbcTemplate.queryForObject("SELECT active FROM artifact_share WHERE resource_id=? AND card_id=?", Boolean.class, resource, cardId);
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

    private void addMembership(UUID spaceId, UUID userId, UUID memberCardId, String role) {
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE',?)",
                spaceId, userId, memberCardId, role);
    }

    private void enableSpace(UUID cardId, UUID actor) {
        ResponseEntity<Map> response = request(HttpMethod.POST, actor, "/v1/cards/" + cardId + "/share", null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID resource(UUID ownerUserId, UUID ownerCardId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO resource(id,owner_user_id,owner_card_id,title,original_filename,mime_type,file_size_bytes,processing_status,original_bytes,extracted_content,image_metadata) " +
                        "VALUES (?,?,?,?,?,'text/plain',0,'READY',decode('78','hex'),'[]'::jsonb,'{}'::jsonb)",
                id, ownerUserId, ownerCardId, title, title + ".txt");
        return id;
    }
}
