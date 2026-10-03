package com.lumira.backend.security;

import com.lumira.backend.test.BaseIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("B11 — Backend BOLA/IDOR and list-query regression tests")
class BackendBolaIntegrationTest extends BaseIntegrationTest {
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID owner;
    private UUID outsider;
    private UUID ownerCard;
    private UUID outsiderCard;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("TRUNCATE flashcard_progress, flashcard, flashcard_set, quiz_attempt_answer_option, quiz_attempt_answer, quiz_attempt, quiz_question_option, quiz_question, quiz, artifact_share, resource, note, study_set, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("b11-owner");
        outsider = user("b11-outsider");
        ownerCard = card(owner, "Owner private Card");
        outsiderCard = card(outsider, "Outsider private Card");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Knowing private Card and artifact UUIDs grants no cross-user read or write access")
    void crossUserArtifactIdsNeverAuthorizeAccess() {
        UUID resourceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO resource(id,owner_card_id,title,original_filename,mime_type,file_size_bytes,processing_status,original_bytes,extracted_content,image_metadata) " +
                        "VALUES (?,?,?,'private.txt','text/plain',1,'READY',decode('78','hex'),'[]'::jsonb,'{}'::jsonb)",
                resourceId, ownerCard, "Private resource");
        UUID noteId = id(request(HttpMethod.POST, owner, "/v1/cards/" + ownerCard + "/notes",
                Map.of("title", "Private note", "content", "secret"), Map.class));
        UUID studySetId = id(request(HttpMethod.POST, owner, "/v1/cards/" + ownerCard + "/studysets",
                Map.of("title", "Private set", "description", "secret"), Map.class));
        UUID quizId = id(request(HttpMethod.POST, owner, "/v1/cards/" + ownerCard + "/quizzes", quizBody(), Map.class));
        UUID flashcardSetId = id(request(HttpMethod.POST, owner, "/v1/cards/" + ownerCard + "/flashcard-sets",
                Map.of("title", "Private cards", "description", "secret", "cards", List.of(
                        Map.of("position", 1, "front", "front", "back", "secret"))), Map.class));

        assertThat(request(HttpMethod.GET, outsider, "/v1/cards/" + ownerCard, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET, outsider, "/v1/cards/" + ownerCard + "/resources", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET, outsider, "/v1/resources/" + resourceId, null, byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.POST, outsider, "/v1/resources/" + resourceId + "/reprocess", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertArtifactDenied(outsider, "notes", noteId, Map.of("title", "takeover"));
        assertArtifactDenied(outsider, "studysets", studySetId, Map.of("title", "takeover"));
        assertArtifactDenied(outsider, "quizzes", quizId, Map.of("title", "takeover"));
        assertArtifactDenied(outsider, "flashcard-sets", flashcardSetId, Map.of("title", "takeover"));
        assertThat(request(HttpMethod.GET, outsider, "/v1/flashcard-sets/" + flashcardSetId + "/progress/me", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM note WHERE id=?", String.class, noteId)).isEqualTo("Private note");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM study_set WHERE id=?", String.class, studySetId)).isEqualTo("Private set");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM quiz WHERE id=?", String.class, quizId)).isEqualTo("Private quiz");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM flashcard_set WHERE id=?", String.class, flashcardSetId)).isEqualTo("Private cards");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM resource WHERE id=?", Integer.class, resourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Card listing pages are bounded, disjoint, and scoped to the authenticated owner")
    void cardListPaginationIsBoundedAndOwnerScoped() {
        UUID newest = card(owner, "Newest");
        UUID middle = card(owner, "Middle");
        UUID oldest = card(owner, "Oldest");
        card(outsider, "Another user's Card");

        Map[] first = request(HttpMethod.GET, owner, "/v1/cards?page=0&pageSize=2", null, Map[].class).getBody();
        Map[] second = request(HttpMethod.GET, owner, "/v1/cards?page=1&pageSize=2", null, Map[].class).getBody();
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        List<String> firstIds = java.util.Arrays.stream(first).map(row -> row.get("id").toString()).toList();
        List<String> secondIds = java.util.Arrays.stream(second).map(row -> row.get("id").toString()).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(firstIds).doesNotContain(outsiderCard.toString());
        assertThat(java.util.stream.Stream.concat(firstIds.stream(), secondIds.stream()).toList())
                .contains(newest.toString(), middle.toString(), oldest.toString());
        assertThat(request(HttpMethod.GET, owner, "/v1/cards?page=-1&pageSize=2", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards?page=0&pageSize=101", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        for (int index = 0; index < 3; index++) {
            UUID sharedCard = card(owner, "Shared " + index);
            assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + sharedCard + "/share", null, Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        Map[] sharedFirst = request(HttpMethod.GET, owner, "/v1/cards?scope=shared&page=0&pageSize=2", null, Map[].class).getBody();
        Map[] sharedSecond = request(HttpMethod.GET, owner, "/v1/cards?scope=shared&page=1&pageSize=2", null, Map[].class).getBody();
        assertThat(sharedFirst).hasSize(2);
        assertThat(sharedSecond).hasSize(1);
        assertThat(java.util.Arrays.stream(sharedFirst).map(row -> row.get("isShared")).toList())
                .allSatisfy(isShared -> assertThat(isShared).isEqualTo(true));
    }

    @Test
    @DisplayName("Flashcard list response query count stays bounded as the number of sets grows")
    void flashcardListAvoidsPerSetQueries() {
        createFlashcardSet("Set one");
        long oneSetQueries = flashcardListQueryCount();

        createFlashcardSet("Set two");
        createFlashcardSet("Set three");
        createFlashcardSet("Set four");
        long fourSetQueries = flashcardListQueryCount();

        assertThat(fourSetQueries).isLessThanOrEqualTo(oneSetQueries + 1);
    }

    private long flashcardListQueryCount() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();
        statistics.clear();
        ResponseEntity<Map[]> response = request(HttpMethod.GET, owner,
                "/v1/cards/" + ownerCard + "/flashcard-sets", null, Map[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return statistics.getPrepareStatementCount();
    }

    private void createFlashcardSet(String title) {
        ResponseEntity<Map> response = request(HttpMethod.POST, owner, "/v1/cards/" + ownerCard + "/flashcard-sets",
                Map.of("title", title, "description", "description", "cards", List.of(
                        Map.of("position", 1, "front", "front", "back", "back"))), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void assertArtifactDenied(UUID actor, String path, UUID artifactId, Object patch) {
        assertThat(request(HttpMethod.GET, actor, "/v1/" + path + "/" + artifactId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, actor, "/v1/" + path + "/" + artifactId, patch, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, actor, "/v1/" + path + "/" + artifactId, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> quizBody() {
        return Map.of("title", "Private quiz", "description", "secret", "questions", List.of(
                Map.of("position", 1, "prompt", "Question", "options", List.of(
                        Map.of("position", 1, "text", "Correct", "correct", true),
                        Map.of("position", 2, "text", "Wrong", "correct", false)))));
    }

    private UUID id(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private <T> ResponseEntity<T> request(HttpMethod method, UUID actor, String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), responseType);
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user(id,email,display_name) VALUES (?,?,?)", id, name + "@example.com", name);
        return id;
    }

    private UUID card(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,false,0,false)",
                id, ownerId, name);
        return id;
    }
}
