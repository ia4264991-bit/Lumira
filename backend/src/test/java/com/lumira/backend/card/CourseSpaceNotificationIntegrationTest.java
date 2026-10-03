package com.lumira.backend.card;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B8 — Course Space events and notifications")
class CourseSpaceNotificationIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID admin;
    private UUID member;
    private UUID invited;
    private UUID outside;
    private UUID space;
    private UUID ownerCard;
    private UUID adminCard;
    private UUID memberCard;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE notification, artifact_share, resource, note, study_set, quiz_attempt_answer_option, quiz_attempt_answer, quiz_attempt, quiz_question_option, quiz_question, quiz, flashcard_progress, flashcard, flashcard_set, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("b8-owner");
        admin = user("b8-admin");
        member = user("b8-member");
        invited = user("b8-invited");
        outside = user("b8-outside");
        ownerCard = card(owner, "Owner personal");
        adminCard = card(admin, "Admin member");
        memberCard = card(member, "Member card");
        space = card(owner, "Course Space");
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + space + "/share", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','ADMIN')",
                space, admin, adminCard);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, member, memberCard);
    }

    @Test
    @DisplayName("Direct invitation and role events notify their explicit recipients, not their actors")
    void recipientPoliciesAndInboxIsolation() {
        ResponseEntity<Map> invitation = request(HttpMethod.POST, owner, "/v1/cards/" + space + "/invitations",
                Map.of("userId", invited), Map.class);
        assertThat(invitation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID invitedMembership = UUID.fromString(invitation.getBody().get("membershipId").toString());
        Map<String, Object>[] invitedInbox = request(HttpMethod.GET, invited, "/v1/notifications", null, Map[].class).getBody();
        assertThat(invitedInbox).hasSize(1);
        assertThat(invitedInbox[0]).containsEntry("type", "MEMBER_INVITED")
                .containsEntry("recipientMembershipId", invitedMembership.toString())
                .containsEntry("recipientUserId", null);
        assertThat(request(HttpMethod.GET, owner, "/v1/notifications", null, Map[].class).getBody()).isEmpty();
        assertThat(request(HttpMethod.GET, outside, "/v1/notifications", null, Map[].class).getBody()).isEmpty();
        UUID inviteEvent = UUID.fromString(invitedInbox[0].get("eventId").toString());

        assertThat(request(HttpMethod.PATCH, outside, "/v1/notifications/" + invitedInbox[0].get("id") + "/read",
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, outside, "/v1/notifications/" + inviteEvent + "/read",
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, outside, "/v1/notifications/" + invitedMembership + "/read",
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT read_at IS NULL FROM notification WHERE id=?", Boolean.class,
                UUID.fromString(invitedInbox[0].get("id").toString()))).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE id=?", Integer.class, inviteEvent))
                .isEqualTo(1);

        assertThat(request(HttpMethod.POST, invited, "/v1/me/invitations/" + invitedMembership + "/accept", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + space + "/members/" + member + "/promote", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='MEMBER_PROMOTED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, member)).isEqualTo(1);

        Map<String, Object>[] ownerInbox = request(HttpMethod.GET, owner, "/v1/notifications", null, Map[].class).getBody();
        assertThat(ownerInbox).extracting(n -> n.get("type")).contains("MEMBER_INVITATION_ACCEPTED").doesNotContain("MEMBER_PROMOTED");
        Map<String, Object>[] adminInbox = request(HttpMethod.GET, admin, "/v1/notifications", null, Map[].class).getBody();
        assertThat(adminInbox).extracting(n -> n.get("type")).contains("MEMBER_PROMOTED");
        assertThat(request(HttpMethod.PATCH, invited, "/v1/notifications/" + invitedInbox[0].get("id") + "/read",
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT read_at IS NOT NULL FROM notification WHERE id=?", Boolean.class,
                UUID.fromString(invitedInbox[0].get("id").toString()))).isTrue();

        ResponseEntity<Map> secondInvite = request(HttpMethod.POST, owner, "/v1/cards/" + space + "/invitations",
                Map.of("userId", outside), Map.class);
        UUID secondMembership = UUID.fromString(secondInvite.getBody().get("membershipId").toString());
        assertThat(request(HttpMethod.POST, outside, "/v1/me/invitations/" + secondMembership + "/decline", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='MEMBER_INVITATION_DECLINED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, owner)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='MEMBER_INVITATION_DECLINED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, member)).isEqualTo(1);
        assertThat(request(HttpMethod.GET, outside, "/v1/notifications", null, Map[].class).getBody())
                .extracting(n -> n.get("type")).doesNotContain("MEMBER_INVITATION_DECLINED");
    }

    @Test
    @DisplayName("Force-unshare notifies an active artifact owner through membership exactly once")
    void forceUnshareUsesMembershipRecipientAndPreservesArtifact() {
        UUID noteId = createAndShareNote(member, memberCard);
        UUID ownerBefore = jdbcTemplate.queryForObject("SELECT owner_card_id FROM note WHERE id=?", UUID.class, noteId);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/force-unshare/" + space,
                Map.of("reason", "PRIVACY"), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, member)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_user_id=?",
                Integer.class, noteId.toString(), member)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM note WHERE id=?", UUID.class, noteId)).isEqualTo(ownerBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM note WHERE id=?", Integer.class, noteId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM artifact_share WHERE note_id=? AND card_id=?", Boolean.class,
                noteId, space)).isFalse();
    }

    @Test
    @DisplayName("A force-unsharing Course Space Owner who owns the artifact still receives exactly one notification")
    void forceUnshareNotifiesOwnerActorOnce() {
        UUID noteId = createAndShareNote(owner, ownerCard);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/force-unshare/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, owner)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_user_id=?",
                Integer.class, noteId.toString(), owner)).isZero();
    }

    @Test
    @DisplayName("Force-unshare reaches an owner who left using a direct user recipient without a duplicate")
    void forceUnshareUsesDirectRecipientAfterOwnerLeaves() {
        UUID noteId = createAndShareNote(member, memberCard);
        assertThat(request(HttpMethod.POST, member, "/v1/cards/" + space + "/leave", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("DELETE FROM notification");

        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/force-unshare/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_user_id=? AND n.recipient_membership_id IS NULL",
                Integer.class, noteId.toString(), member)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_FORCE_UNSHARED' AND e.payload->>'artifactId'=? AND (n.recipient_user_id=? OR n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='LEFT'))",
                Integer.class, noteId.toString(), member, space, member)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM note WHERE id=?", UUID.class, noteId)).isEqualTo(memberCard);
    }

    @Test
    @DisplayName("Ownership transfer explicitly notifies both Owners, including the initiating former Owner")
    void ownershipTransferNotifiesBothAffectedOwners() {
        UUID formerOwnerMembership = jdbcTemplate.queryForObject("SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE'",
                UUID.class, space, owner);
        UUID newOwnerMembership = jdbcTemplate.queryForObject("SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE'",
                UUID.class, space, member);
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + space + "/transfer-ownership",
                Map.of("targetUserId", member), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='OWNERSHIP_TRANSFERRED' AND n.recipient_membership_id=?",
                Integer.class, formerOwnerMembership)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='OWNERSHIP_TRANSFERRED' AND n.recipient_membership_id=?",
                Integer.class, newOwnerMembership)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='OWNERSHIP_TRANSFERRED' AND n.recipient_user_id IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sharing and voluntary unsharing notify other active members but not the artifact-owner actor")
    void ordinarySharingEventsExcludeTheirActor() {
        UUID noteId = createAndShareNote(member, memberCard);
        assertThat(request(HttpMethod.GET, outside, "/v1/notifications?artifactId=" + noteId, null, Map[].class).getBody())
                .isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='ARTIFACT_SHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, owner)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='ARTIFACT_SHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, member)).isZero();

        assertThat(request(HttpMethod.DELETE, member, "/v1/artifacts/note/" + noteId + "/share/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, admin)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='CONTENT_UNSHARED' AND e.payload->>'artifactId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, noteId.toString(), space, member)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM note WHERE id=?", Integer.class, noteId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Resource publication emits one event and notifies active members other than the uploader")
    void resourcePublicationUsesSharedEventPath() {
        ResponseEntity<Map> uploaded = uploadResource(admin, "course.txt", "course resource text");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID resourceId = UUID.fromString(uploaded.getBody().get("id").toString());
        assertThat(uploaded.getBody().get("status")).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='RESOURCE_ADDED' AND e.payload->>'resourceId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, resourceId.toString(), space, owner)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='RESOURCE_ADDED' AND e.payload->>'resourceId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, resourceId.toString(), space, member)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='RESOURCE_ADDED' AND e.payload->>'resourceId'=? AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, resourceId.toString(), space, admin)).isZero();
    }

    @Test
    @DisplayName("Removal notifies the removed member and dissolution notifies remaining active members")
    void targetedRemovalAndDissolutionPolicies() {
        UUID memberMembership = jdbcTemplate.queryForObject("SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE'",
                UUID.class, space, member);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/cards/" + space + "/members/" + member, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='MEMBER_REMOVED' AND n.recipient_membership_id=?",
                Integer.class, memberMembership)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='MEMBER_REMOVED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, owner)).isZero();

        assertThat(request(HttpMethod.DELETE, owner, "/v1/cards/" + space + "/share", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='COURSE_SPACE_DISSOLVED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, admin)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification n JOIN course_space_event e ON e.id=n.event_id WHERE e.type='COURSE_SPACE_DISSOLVED' AND n.recipient_membership_id=(SELECT id FROM card_membership WHERE card_id=? AND user_id=? AND status='ACTIVE')",
                Integer.class, space, owner)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL enforces one recipient, a real event reference, and event retention")
    void databaseInvariantsAndEventHistory() {
        UUID eventId = jdbcTemplate.queryForObject("SELECT id FROM course_space_event WHERE card_id=? ORDER BY created_at LIMIT 1",
                UUID.class, space);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO notification(event_id,recipient_membership_id,recipient_user_id) VALUES (?,?,?)",
                eventId, UUID.randomUUID(), outside)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO notification(event_id) VALUES (?)", eventId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO notification(event_id,recipient_membership_id) VALUES (?,?)",
                UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(RuntimeException.class);

        UUID noteId = createAndShareNote(member, memberCard);
        UUID shareEventId = jdbcTemplate.queryForObject("SELECT id FROM course_space_event WHERE card_id=? AND type='ARTIFACT_SHARED' ORDER BY created_at DESC LIMIT 1",
                UUID.class, space);
        Integer eventCountBeforeRejectedAction = jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id=?",
                Integer.class, space);
        assertThat(request(HttpMethod.DELETE, outside, "/v1/cards/" + space + "/members/" + member, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id=?", Integer.class, space))
                .isEqualTo(eventCountBeforeRejectedAction);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE id=?", Integer.class, shareEventId))
                .isEqualTo(1);
        assertThat(request(HttpMethod.DELETE, member, "/v1/notes/" + noteId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE id=?", Integer.class, shareEventId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE event_id=?", Integer.class, shareEventId))
                .isGreaterThan(0);

        UUID removedMembership = jdbcTemplate.queryForObject("SELECT id FROM card_membership WHERE card_id=? AND user_id=?",
                UUID.class, space, member);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/cards/" + space + "/members/" + member, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbcTemplate.update("DELETE FROM card_membership WHERE id=?", removedMembership);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE id=?", Integer.class, shareEventId))
                .isEqualTo(1);
    }

    private UUID createAndShareNote(UUID actor, UUID actorCard) {
        ResponseEntity<Map> created = request(HttpMethod.POST, actor, "/v1/cards/" + actorCard + "/notes",
                Map.of("title", "Shared note", "content", "content"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID noteId = UUID.fromString(created.getBody().get("id").toString());
        assertThat(request(HttpMethod.POST, actor, "/v1/artifacts/note/" + noteId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return noteId;
    }

    private ResponseEntity<Map> uploadResource(UUID actor, String filename, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        ByteArrayResource file = new ByteArrayResource(content.getBytes()) {
            @Override public String getFilename() { return filename; }
        };
        parts.add("file", new HttpEntity<>(file, fileHeaders));
        parts.add("title", filename);
        return restTemplate.postForEntity(url("/v1/cards/" + space + "/resources"),
                new HttpEntity<>(parts, headers), Map.class);
    }

    private <T> ResponseEntity<T> request(HttpMethod method, UUID actor, String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
    }

    private UUID user(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user(id,email,display_name) VALUES (?,?,?)", id, suffix + "@example.com", suffix);
        return id;
    }

    private UUID card(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,false,0,false)",
                id, ownerId, name);
        return id;
    }
}
