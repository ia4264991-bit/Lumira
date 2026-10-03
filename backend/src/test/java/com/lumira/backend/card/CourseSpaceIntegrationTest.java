package com.lumira.backend.card;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B2 â€” Course Spaces")
class CourseSpaceIntegrationTest extends BaseIntegrationTest {

    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID otherId;
    private UUID rejectId;
    private UUID cardId;

    @BeforeEach
    void createUsersAndClearDatabase() {
        jdbcTemplate.execute("TRUNCATE artifact_share, resource, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        ownerId = addUser("owner");
        adminId = addUser("admin");
        memberId = addUser("member");
        otherId = addUser("other");
        rejectId = addUser("reject");
        ResponseEntity<CardResponse> created = createCard(ownerId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        cardId = created.getBody().id();
    }

    @Test
    @DisplayName("direct invites are private until acceptance, idempotent, and reusable after leave/removal")
    void directInvitationAndMembershipEpisodes() {
        enableSharing();
        ResponseEntity<Map> firstInvite = invite(ownerId, memberId);
        assertThat(firstInvite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map inviteBody = firstInvite.getBody();
        UUID firstMembershipId = UUID.fromString((String) inviteBody.get("membershipId"));
        UUID memberCardId = UUID.fromString((String) inviteBody.get("memberCardId"));
        assertThat(inviteBody.get("status")).isEqualTo("INVITED");

        ResponseEntity<Map> repeatedInvite = invite(ownerId, memberId);
        assertThat(repeatedInvite.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeatedInvite.getBody().get("membershipId")).isEqualTo(firstMembershipId.toString());
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=?", cardId, memberId)).isEqualTo(1);
        assertThat(count("select count(*) from card where owner_id=?", memberId)).isEqualTo(1);

        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getSharedCards(memberId).getBody()).isEmpty();
        assertThat(rest("GET", otherId, "/v1/me/invitations/" + firstMembershipId, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(invitation(otherId, firstMembershipId, "accept").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(invitation(memberId, firstMembershipId, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getSharedCards(memberId).getBody()).hasSize(1);
        assertThat(invite(ownerId, memberId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status in ('ACTIVE','INVITED')",
                cardId, memberId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("insert into card_membership(id,card_id,user_id,member_card_id,status,role) values (?,?,?,?,?,?)",
                UUID.randomUUID(), cardId, memberId, memberCardId, "ACTIVE", "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(membershipAction(ownerId, "promote", memberId).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> adminInvite = invite(memberId, otherId);
        assertThat(adminInvite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(membershipAction(memberId, "promote", otherId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getCard(otherId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        UUID otherInvitation = UUID.fromString((String) adminInvite.getBody().get("membershipId"));
        assertThat(invitation(otherId, otherInvitation, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(otherId, cardId).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(removeMember(ownerId, otherId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(otherId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> rejoinInvitation = invite(ownerId, otherId);
        assertThat(rejoinInvitation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rejoinInvitation.getBody().get("memberCardId")).isEqualTo(
                jdbcTemplate.queryForObject("select member_card_id from card_membership where id=?", String.class, otherInvitation));
        assertThat(invitation(otherId, UUID.fromString((String) rejoinInvitation.getBody().get("membershipId")), "accept")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status='REMOVED'", cardId, otherId)).isEqualTo(1);
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status='ACTIVE'", cardId, otherId)).isEqualTo(1);

        assertThat(rest("DELETE", ownerId, "/v1/cards/" + cardId + "/members/" + ownerId, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("select count(*) from course_space_event where card_id=? and type='MEMBER_INVITED'", cardId)).isEqualTo(3);
    }

    @Test
    @DisplayName("invitees may decline; decline retains the episode and blocks access")
    void decliningInvitationRetainsHistory() {
        enableSharing();
        ResponseEntity<Map> invited = invite(ownerId, memberId);
        UUID invitationId = UUID.fromString((String) invited.getBody().get("membershipId"));
        UUID memberCard = UUID.fromString((String) invited.getBody().get("memberCardId"));
        assertThat(invitation(memberId, invitationId, "decline").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("select status from card_membership where id=?", String.class, invitationId))
                .isEqualTo("LEFT");
        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> reinvited = invite(ownerId, memberId);
        assertThat(reinvited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reinvited.getBody().get("memberCardId")).isEqualTo(memberCard.toString());
        assertThat(reinvited.getBody().get("membershipId")).isNotEqualTo(invitationId.toString());
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status='LEFT'", cardId, memberId)).isEqualTo(1);
        assertThat(count("select count(*) from card where owner_id=?", memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("link joins create/reuse a member Card; reset invalidates pending requests and stale links")
    void publicLinkAndApprovalLifecycle() {
        enableSharing();
        Map link = rest("POST", ownerId, "/v1/cards/" + cardId + "/share-link", null).getBody();
        String oldToken = (String) link.get("shareToken");
        assertThat(rest("POST", memberId, "/v1/join/" + oldToken, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID originalMemberCard = UUID.fromString(jdbcTemplate.queryForObject(
                "select member_card_id from card_membership where card_id=? and user_id=? and status='ACTIVE'",
                String.class, cardId, memberId));
        assertThat(rest("POST", memberId, "/v1/cards/" + cardId + "/leave", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map secondJoin = rest("POST", memberId, "/v1/join/" + oldToken, null).getBody();
        assertThat(secondJoin.get("id")).isEqualTo(originalMemberCard.toString());
        assertThat(count("select count(*) from card where owner_id=?", memberId)).isEqualTo(1);
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status='LEFT'", cardId, memberId)).isEqualTo(1);

        assertThat(rest("PATCH", ownerId, "/v1/cards/" + cardId + "/share-link/approval", Map.of("requireApproval", true))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        Map secondLink = rest("POST", ownerId, "/v1/cards/" + cardId + "/share-link", null).getBody();
        String pendingToken = (String) secondLink.get("shareToken");
        ResponseEntity<Map> pending = rest("POST", otherId, "/v1/join/" + pendingToken, null);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID requestId = UUID.fromString((String) pending.getBody().get("joinRequestId"));
        assertThat(count("select count(*) from card_join_request where id=? and status='PENDING'", requestId)).isEqualTo(1);
        assertThat(count("select count(*) from card where owner_id=?", otherId)).isZero();

        Map reset = rest("POST", ownerId, "/v1/cards/" + cardId + "/share-link", null).getBody();
        assertThat(rest("POST", otherId, "/v1/join/" + pendingToken, null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(jdbcTemplate.queryForObject("select status from card_join_request where id=?", String.class, requestId))
                .isEqualTo("INVALIDATED");
        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/join-requests/" + requestId + "/approve", null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String currentToken = (String) reset.get("shareToken");
        ResponseEntity<Map> newRequest = rest("POST", otherId, "/v1/join/" + currentToken, null);
        assertThat(newRequest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID newRequestId = UUID.fromString((String) newRequest.getBody().get("joinRequestId"));
        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/join-requests/" + newRequestId + "/approve", null)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(getCard(otherId, cardId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("select count(*) from card where owner_id=?", otherId)).isEqualTo(1);

        ResponseEntity<Map> rejectedRequest = rest("POST", rejectId, "/v1/join/" + currentToken, null);
        assertThat(rejectedRequest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID rejectedId = UUID.fromString((String) rejectedRequest.getBody().get("joinRequestId"));
        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/join-requests/" + rejectedId + "/reject", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("select status from card_join_request where id=?", String.class, rejectedId))
                .isEqualTo("REJECTED");
        assertThat(count("select count(*) from card where owner_id=?", rejectId)).isZero();
    }

    @Test
    @DisplayName("only active members access shared Cards; admins cannot withdraw or mutate memberships")
    void authorizationAndIsolation() {
        enableSharing();
        UUID invitedId = UUID.fromString((String) invite(ownerId, adminId).getBody().get("membershipId"));
        assertThat(invitation(adminId, invitedId, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(membershipAction(ownerId, "promote", adminId).getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID pendingId = UUID.fromString((String) invite(adminId, memberId).getBody().get("membershipId"));
        UUID activeMemberInviteId = UUID.fromString((String) invite(adminId, otherId).getBody().get("membershipId"));
        assertThat(invitation(otherId, activeMemberInviteId, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest("DELETE", adminId, "/v1/cards/" + cardId + "/invitations/" + pendingId, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest("DELETE", adminId, "/v1/cards/" + cardId + "/members/" + otherId, null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getCard(rejectId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        UUID separateCourseSpaceId = createCard(ownerId).getBody().id();
        assertThat(rest("POST", ownerId, "/v1/cards/" + separateCourseSpaceId + "/share", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(getCard(otherId, separateCourseSpaceId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(rest("DELETE", ownerId, "/v1/cards/" + cardId + "/invitations/" + pendingId, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("select status from card_membership where id=?", String.class, pendingId))
                .isEqualTo("REMOVED");
        assertThat(getCard(memberId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(removeMember(ownerId, adminId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(adminId, cardId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ownership transfer relinks member Cards atomically and former Owner can leave and rejoin")
    void ownershipTransferRelinksCardsAndSupportsFormerOwnerRejoin() {
        enableSharing();
        UUID targetInvitation = UUID.fromString((String) invite(ownerId, memberId).getBody().get("membershipId"));
        assertThat(invitation(memberId, targetInvitation, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID targetPersonalCardId = UUID.fromString(jdbcTemplate.queryForObject(
                "select member_card_id from card_membership where id=?", String.class, targetInvitation));
        assertOwnerInvariant(ownerId);

        ResponseEntity<Map> transfer = transfer(ownerId, memberId);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("select owner_id from card where id=?", String.class, cardId))
                .isEqualTo(memberId.toString());
        assertThat(jdbcTemplate.queryForObject(
                "select role from card_membership where card_id=? and user_id=? and status='ACTIVE'", String.class, cardId, memberId))
                .isEqualTo("OWNER");
        assertThat(jdbcTemplate.queryForObject(
                "select role from card_membership where card_id=? and user_id=? and status='ACTIVE'", String.class, cardId, ownerId))
                .isEqualTo("ADMIN");

        UUID formerOwnerCardId = UUID.fromString(jdbcTemplate.queryForObject(
                "select member_card_id from card_membership where card_id=? and user_id=? and status='ACTIVE'",
                String.class, cardId, ownerId));
        assertThat(formerOwnerCardId).isNotEqualTo(cardId);
        assertThat(jdbcTemplate.queryForObject("select owner_id from card where id=?", String.class, formerOwnerCardId))
                .isEqualTo(ownerId.toString());
        assertThat(jdbcTemplate.queryForObject("select name from card where id=?", String.class, formerOwnerCardId))
                .isEqualTo("Course");
        assertThat(jdbcTemplate.queryForObject(
                "select member_card_id from card_membership where card_id=? and user_id=? and status='ACTIVE'",
                String.class, cardId, memberId)).isEqualTo(cardId.toString());
        assertThat(jdbcTemplate.queryForObject("select owner_id from card where id=?", String.class, targetPersonalCardId))
                .isEqualTo(memberId.toString());
        assertOwnerInvariant(memberId);
        assertThat(count("select count(*) from course_space_event where card_id=? and type='OWNERSHIP_TRANSFERRED' and actor_user_id=?",
                cardId, ownerId)).isEqualTo(1);

        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/leave", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject(
                "select status from card_membership where card_id=? and user_id=?", String.class, cardId, ownerId))
                .isEqualTo("LEFT");
        Map link = rest("POST", memberId, "/v1/cards/" + cardId + "/share-link", null).getBody();
        ResponseEntity<Map> rejoin = rest("POST", ownerId, "/v1/join/" + link.get("shareToken"), null);
        assertThat(rejoin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rejoin.getBody().get("id")).isEqualTo(formerOwnerCardId.toString());
        // Transfer creates one replacement personal Card for the former Owner;
        // rejoin must reuse it instead of creating a second Card.
        assertThat(count("select count(*) from card where owner_id=?", ownerId)).isEqualTo(1);
        assertThat(count("select count(*) from card_membership where card_id=? and user_id=? and status='ACTIVE'",
                cardId, ownerId)).isEqualTo(1);
        assertOwnerInvariant(memberId);
    }

    @Test
    @DisplayName("only the Owner can transfer to another ACTIVE member and self-transfer is rejected")
    void ownershipTransferAuthorizationAndTargetValidation() {
        enableSharing();
        UUID activeInvite = UUID.fromString((String) invite(ownerId, memberId).getBody().get("membershipId"));
        assertThat(invitation(memberId, activeInvite, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID pendingInvite = UUID.fromString((String) invite(ownerId, adminId).getBody().get("membershipId"));

        assertThat(transfer(memberId, ownerId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(transfer(ownerId, ownerId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(transfer(ownerId, adminId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject("select status from card_membership where id=?", String.class, pendingInvite))
                .isEqualTo("INVITED");

        Map link = rest("POST", ownerId, "/v1/cards/" + cardId + "/share-link", null).getBody();
        assertThat(rest("POST", rejectId, "/v1/join/" + link.get("shareToken"), null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(rest("POST", rejectId, "/v1/cards/" + cardId + "/leave", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transfer(ownerId, rejectId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertOwnerInvariant(ownerId);
    }

    private ResponseEntity<Map> transfer(UUID actor, UUID target) {
        return rest("POST", actor, "/v1/cards/" + cardId + "/transfer-ownership", Map.of("targetUserId", target));
    }

    private void assertOwnerInvariant(UUID expectedOwnerId) {
        assertThat(jdbcTemplate.queryForObject("select owner_id from card where id=?", String.class, cardId))
                .isEqualTo(expectedOwnerId.toString());
        assertThat(count("select count(*) from card_membership where card_id=? and status='ACTIVE' and role='OWNER'",
                cardId)).isEqualTo(1);
        assertThat(count("select count(*) from card_membership where card_id=? and status='ACTIVE' and role='OWNER' and user_id=? and member_card_id=?",
                cardId, expectedOwnerId, cardId)).isEqualTo(1);
    }

    private UUID addUser(String prefix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into app_user(id,email,display_name) values (?,?,?)",
                id, prefix + "-" + id + "@example.com", prefix);
        return id;
    }

    private ResponseEntity<CardResponse> createCard(UUID userId) {
        return restTemplate.postForEntity(url("/v1/cards"), request(userId, Map.of("name", "Course")), CardResponse.class);
    }

    private void enableSharing() {
        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/share", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCard(ownerId, cardId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest("POST", ownerId, "/v1/cards/" + cardId + "/leave", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private ResponseEntity<Map> invite(UUID actor, UUID target) {
        return rest("POST", actor, "/v1/cards/" + cardId + "/invitations", Map.of("userId", target));
    }

    private ResponseEntity<Map> invitation(UUID actor, UUID invitationId, String action) {
        return rest("POST", actor, "/v1/me/invitations/" + invitationId + "/" + action, null);
    }

    private ResponseEntity<Map> membershipAction(UUID actor, String action, UUID target) {
        return rest("POST", actor, "/v1/cards/" + cardId + "/members/" + target + "/" + action, null);
    }

    private ResponseEntity<Map> removeMember(UUID actor, UUID target) {
        return rest("DELETE", actor, "/v1/cards/" + cardId + "/members/" + target, null);
    }

    private ResponseEntity<CardResponse> getCard(UUID actor, UUID id) {
        return restTemplate.exchange(url("/v1/cards/" + id), HttpMethod.GET, request(actor, null), CardResponse.class);
    }

    private ResponseEntity<CardResponse[]> getSharedCards(UUID actor) {
        return restTemplate.exchange(url("/v1/cards?scope=shared"), HttpMethod.GET, request(actor, null), CardResponse[].class);
    }

    private ResponseEntity<Map> rest(String method, UUID actor, String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.valueOf(method), request(actor, body), Map.class);
    }

    private HttpEntity<Object> request(UUID actor, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Integer count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
