package com.lumira.backend.flashcard;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B7 — Flashcards and private progress")
class FlashcardIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID member;
    private UUID stranger;
    private UUID spaceOwner;
    private UUID personalCard;
    private UUID space;
    private UUID memberCard;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE flashcard_progress, flashcard, flashcard_set, quiz_attempt_answer_option, quiz_attempt_answer, quiz_attempt, quiz_question_option, quiz_question, quiz, artifact_share, resource, note, study_set, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("b7-owner");
        member = user("b7-member");
        stranger = user("b7-stranger");
        spaceOwner = user("b7-space-owner");
        personalCard = card(owner, "Personal", false);
        space = card(spaceOwner, "Course Space", false);
        assertThat(request(HttpMethod.POST, spaceOwner, "/v1/cards/" + space + "/share", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        UUID ownerMemberCard = card(owner, "Owner member Card", false);
        memberCard = card(member, "Member Card", false);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, owner, ownerMemberCard);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, member, memberCard);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Canonical CRUD and explicit sharing use live authorization and deterministic card ordering")
    void canonicalCrudAndSharing() {
        UUID setId = createSet(personalCard);
        Map<String, Object> created = request(HttpMethod.GET, owner, "/v1/flashcard-sets/" + setId, null, Map.class).getBody();
        List<Map<String, Object>> cards = (List<Map<String, Object>>) created.get("cards");
        assertThat(cards).extracting(card -> card.get("position")).containsExactly(1, 2);
        UUID firstCard = UUID.fromString(cards.getFirst().get("id").toString());
        UUID secondCard = UUID.fromString(cards.get(1).get("id").toString());
        assertThat(request(HttpMethod.GET, stranger, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, stranger, "/v1/flashcard-sets/" + setId, Map.of("title", "takeover"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/flashcard_set/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> shared = request(HttpMethod.GET, member, "/v1/flashcard-sets/" + setId, null, Map.class).getBody();
        assertThat(shared).doesNotContainKey("progress");
        assertThat(request(HttpMethod.GET, stranger, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET, member, "/v1/cards/" + space + "/flashcard-sets", null, Map[].class).getBody())
                .hasSize(1);

        jdbcTemplate.update("UPDATE card_membership SET status='REMOVED' WHERE card_id=? AND user_id=?", space, member);
        assertThat(request(HttpMethod.GET, member, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> replacement = Map.of("cards", List.of(
                Map.of("id", secondCard, "position", 1, "front", "second moved", "back", "second answer"),
                Map.of("id", firstCard, "position", 2, "front", "first moved", "back", "first answer")));
        Map<String, Object> updated = request(HttpMethod.PATCH, owner, "/v1/flashcard-sets/" + setId, replacement, Map.class).getBody();
        List<Map<String, Object>> ordered = (List<Map<String, Object>>) updated.get("cards");
        assertThat(ordered).extracting(card -> card.get("id")).containsExactly(secondCard.toString(), firstCard.toString());
        assertThat(request(HttpMethod.DELETE, owner, "/v1/artifacts/flashcard_set/" + setId + "/share/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, member, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flashcard_set WHERE id=?", Integer.class, setId)).isEqualTo(1);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void courseSpaceFlashcardsRemainPrivateUntilExplicitShare() {
        Map<String, Object> create = Map.of("title", "Owner private set", "description", "Private",
                "cards", List.of(Map.of("position", 1, "front", "Q", "back", "A")));
        ResponseEntity<Map> response = request(HttpMethod.POST, spaceOwner,
                "/v1/cards/" + space + "/flashcard-sets", create, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID setId = UUID.fromString(response.getBody().get("id").toString());
        assertThat(response.getBody().get("sharedWithThisCourseSpace")).isEqualTo(false);
        assertThat(request(HttpMethod.GET, member, "/v1/cards/" + space + "/flashcard-sets", null, Map[].class).getBody()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE flashcard_set_id=? AND active",
                Integer.class, setId)).isZero();
        assertThat(request(HttpMethod.POST, member, "/v1/cards/" + space + "/flashcard-sets",
                create, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        request(HttpMethod.POST, spaceOwner, "/v1/artifacts/flashcard_set/" + setId + "/share",
                Map.of("cardId", space), Map.class);
        Map[] shared = request(HttpMethod.GET, member, "/v1/cards/" + space + "/flashcard-sets", null, Map[].class).getBody();
        assertThat(shared).hasSize(1);
        assertThat(shared[0].get("sharedWithThisCourseSpace")).isEqualTo(true);
    }

    @Test
    void courseSpaceDissolutionDeactivatesOnlyItsFlashcardSetShare() {
        UUID setId = createSet(personalCard);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/flashcard_set/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.DELETE, spaceOwner, "/v1/cards/" + space + "/share", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM artifact_share WHERE flashcard_set_id=? AND card_id=?",
                Boolean.class, setId, space)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flashcard_set WHERE id=?", Integer.class, setId)).isEqualTo(1);
        assertThat(request(HttpMethod.GET, member, "/v1/flashcard-sets/" + setId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Only the caller's latest per-card outcome is returned, and only AGAIN/GOT_IT are stored")
    void progressIsPrivatePerUserAndCard() {
        UUID setId = createSet(personalCard);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/flashcard_set/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = request(HttpMethod.GET, owner, "/v1/flashcard-sets/" + setId, null, Map.class).getBody();
        UUID flashcardId = UUID.fromString(((Map<String, Object>) ((List<?>) body.get("cards")).getFirst()).get("id").toString());
        assertThat(request(HttpMethod.POST, owner, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "AGAIN"), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, owner, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "GOT_IT"), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, member, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "AGAIN"), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, stranger, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "GOT_IT"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST, owner, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", UUID.randomUUID(), "outcome", "GOT_IT"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST, owner, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "MAYBE"), Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object>[] ownerProgress = request(HttpMethod.GET, owner,
                "/v1/flashcard-sets/" + setId + "/progress/me", null, Map[].class).getBody();
        Map<String, Object>[] memberProgress = request(HttpMethod.GET, member,
                "/v1/flashcard-sets/" + setId + "/progress/me", null, Map[].class).getBody();
        assertThat(ownerProgress).hasSize(1);
        assertThat(ownerProgress[0]).containsEntry("outcome", "GOT_IT");
        assertThat(memberProgress).hasSize(1);
        assertThat(memberProgress[0]).containsEntry("outcome", "AGAIN");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flashcard_progress WHERE flashcard_id=?", Integer.class,
                flashcardId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flashcard_progress WHERE user_id=?", Integer.class,
                member)).isEqualTo(1);
    }

    @Test
    void postgresConstraintsEnforceOwnerPositionAndOutcomeBoundaries() {
        UUID setId = createSet(personalCard);
        UUID cardId = jdbcTemplate.queryForObject("SELECT id FROM flashcard WHERE flashcard_set_id=? LIMIT 1", UUID.class, setId);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO flashcard_set(title,description) VALUES ('bad','bad')"))
                .hasMessageContaining("flashcard_set_exactly_one_owner");
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO flashcard_set(owner_card_id,owner_user_id,title,description) VALUES (?,?,?,?)",
                personalCard, owner, "bad", "bad")).hasMessageContaining("flashcard_set_exactly_one_owner");
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO flashcard(flashcard_set_id,position,front,back) VALUES (?,?,?,?)",
                setId, 1, "duplicate", "position")).hasMessageContaining("flashcard_set_position_unique");
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO flashcard_progress(user_id,flashcard_id,outcome) VALUES (?,?,?)",
                owner, cardId, "SCHEDULED")).hasMessageContaining("flashcard_progress_outcome_check");
    }

    @Test
    void accountDeletionTransfersSharedFlashcardSetAndRemovesOnlyDeletingUsersProgress() {
        UUID setId = createSet(personalCard);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/flashcard_set/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> set = request(HttpMethod.GET, owner, "/v1/flashcard-sets/" + setId, null, Map.class).getBody();
        UUID flashcardId = UUID.fromString(((Map<String, Object>) ((List<?>) set.get("cards")).getFirst()).get("id").toString());
        request(HttpMethod.POST, owner, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "AGAIN"), Map.class);
        request(HttpMethod.POST, member, "/v1/flashcard-sets/" + setId + "/progress",
                Map.of("cardId", flashcardId, "outcome", "GOT_IT"), Map.class);

        assertThat(request(HttpMethod.DELETE, owner, "/v1/me", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM flashcard_set WHERE id=?", UUID.class, setId))
                .isEqualTo(space);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE flashcard_set_id=? AND card_id=? AND active",
                Integer.class, setId, space)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flashcard_progress WHERE flashcard_id=?", Integer.class,
                flashcardId)).isEqualTo(1);
        assertThat(request(HttpMethod.GET, member, "/v1/flashcard-sets/" + setId + "/progress/me", null, Map[].class).getBody())
                .hasSize(1);
    }

    @Test
    void studyArtifactsStayPrivateWhenCardIsSharedAndAdminsUseTheirLinkedCard() {
        UUID personalSet = createSet(personalCard);
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/share", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE flashcard_set_id=? AND card_id=? AND active",
                Integer.class, personalSet, personalCard)).isZero();

        jdbcTemplate.update("UPDATE card_membership SET role='ADMIN' WHERE card_id=? AND user_id=?", space, member);
        Map<String, Object> create = Map.of("title", "Course Set", "description", "Admin-created",
                "cards", List.of(Map.of("position", 1, "front", "Q", "back", "A")));
        ResponseEntity<Map> response = request(HttpMethod.POST, member,
                "/v1/cards/" + space + "/flashcard-sets", create, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        response = request(HttpMethod.POST, member,
                "/v1/cards/" + memberCard + "/flashcard-sets", create, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID courseSetId = UUID.fromString(response.getBody().get("id").toString());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE flashcard_set_id=? AND card_id=? AND active",
                Integer.class, courseSetId, space)).isZero();
    }

    private UUID createSet(UUID cardId) {
        Map<String, Object> create = Map.of("title", "Biology", "description", "Cells", "cards", List.of(
                Map.of("position", 1, "front", "Mitochondria", "back", "Energy"),
                Map.of("position", 2, "front", "Nucleus", "back", "DNA")));
        ResponseEntity<Map> response = request(HttpMethod.POST, owner, "/v1/cards/" + cardId + "/flashcard-sets", create, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
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

    private UUID card(UUID ownerId, String name, boolean shared) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,?,0,false)",
                id, ownerId, name, shared);
        return id;
    }
}
