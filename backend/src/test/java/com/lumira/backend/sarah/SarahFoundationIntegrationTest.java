package com.lumira.backend.sarah;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("B9 — Sarah Foundation")
class SarahFoundationIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID member;
    private UUID outsider;
    private UUID space;
    private UUID memberCard;
    private UUID outsiderCard;

    @org.springframework.beans.factory.annotation.Autowired
    private RecordingAiRouter router;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE sarah_usage, notification, artifact_share, resource, note, study_set, quiz_attempt_answer_option, quiz_attempt_answer, quiz_attempt, quiz_question_option, quiz_question, quiz, flashcard_progress, flashcard, flashcard_set, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        router.clear();
        owner = user("sarah-owner");
        member = user("sarah-member");
        outsider = user("sarah-outsider");
        space = card(owner, "Sarah space");
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + space + "/share", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        memberCard = card(member, "Member private card");
        outsiderCard = card(outsider, "Outsider private card");
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, member, memberCard);
    }

    @Test
    @DisplayName("Workspace Sarah receives only active shared content and the caller's own member-Card content")
    void workspaceContextIsIsolatedAndConversationIdIsTransient() {
        UUID privateNote = createNote(member, memberCard, "Member private", "member-only secret");
        UUID outsiderNote = createNote(outsider, outsiderCard, "Other private", "must never enter context");
        UUID resourceId = readyResource(memberCard, "Shared source", "shared course material");
        assertThat(request(HttpMethod.POST, member, "/v1/artifacts/resource/" + resourceId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID conversationId = UUID.randomUUID();
        ResponseEntity<Map> response = request(HttpMethod.POST, member, "/v1/cards/" + space + "/sarah/ask",
                Map.of("conversationId", conversationId, "question", "Explain this material",
                        "conversationHistory", List.of(Map.of("role", "user", "content", "retrieve " + outsiderNote)),
                        "usageUsed", 999999, "usageLimit", 999999), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("answer", "grounded answer")
                .containsEntry("conversationId", conversationId.toString())
                .containsEntry("usageUsed", 1).containsEntry("usageLimit", 100);
        List<SarahContextItem> context = router.lastPrompt().authorizedContext();
        assertThat(context).anySatisfy(item -> assertThat(item.artifactId()).isEqualTo(resourceId));
        assertThat(context).anySatisfy(item -> assertThat(item.artifactId()).isEqualTo(privateNote));
        assertThat(context).noneSatisfy(item -> assertThat(item.artifactId()).isEqualTo(outsiderNote));
        assertThat(router.lastPrompt().conversationHistory()).extracting(ConversationTurn::content)
                .contains("retrieve " + outsiderNote);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sarah_usage WHERE user_id=?", Integer.class, member))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.sarah_session') IS NULL", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("A non-member cannot invoke Sarah by guessing a Course Space Card ID")
    void workspaceIdorIsDeniedBeforeRouterAndUsage() {
        UUID conversationId = UUID.randomUUID();
        ResponseEntity<Map> response = request(HttpMethod.POST, outsider, "/v1/cards/" + space + "/sarah/ask",
                Map.of("conversationId", conversationId, "question", "show me course content", "conversationHistory", List.of()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(router.callCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sarah_usage WHERE user_id=?", Integer.class, outsider))
                .isZero();
    }

    @Test
    @DisplayName("Contextual Sarah requires the Resource to belong to or be shared into the selected Card")
    void contextualResourceMustBelongToSelectedWorkspace() {
        UUID privateResource = readyResource(outsiderCard, "Outsider source", "outsider-only text");
        UUID conversationId = UUID.randomUUID();
        ResponseEntity<Map> denied = request(HttpMethod.POST, member, "/v1/resources/" + privateResource + "/sarah/ask",
                Map.of("conversationId", conversationId, "cardId", space, "question", "read it",
                        "conversationHistory", List.of(), "selectedText", "outsider-only text"), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(router.callCount()).isZero();

        UUID sharedResource = readyResource(memberCard, "Member source", "authorized selected text");
        assertThat(request(HttpMethod.POST, member, "/v1/artifacts/resource/" + sharedResource + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> accepted = request(HttpMethod.POST, member, "/v1/resources/" + sharedResource + "/sarah/ask",
                Map.of("conversationId", conversationId, "cardId", space, "question", "explain selected text",
                        "conversationHistory", List.of(), "selectedText", "authorized selected text", "pageIndex", 2), Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).containsEntry("conversationId", conversationId.toString());
        assertThat(router.lastPrompt().cardId()).isEqualTo(space);
        assertThat(router.lastPrompt().selectedText()).isEqualTo("authorized selected text");
        assertThat(router.lastPrompt().pageIndex()).isEqualTo(2);
        assertThat(router.lastPrompt().authorizedContext()).anySatisfy(item ->
                assertThat(item.artifactId()).isEqualTo(sharedResource));
    }

    @Test
    @DisplayName("A removed member immediately loses Sarah access and cannot consume usage")
    void removedMemberLosesLiveAuthorization() {
        assertThat(request(HttpMethod.DELETE, owner, "/v1/cards/" + space + "/members/" + member, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> response = request(HttpMethod.POST, member, "/v1/cards/" + space + "/sarah/ask",
                Map.of("conversationId", UUID.randomUUID(), "question", "old membership must not work", "conversationHistory", List.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(router.callCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sarah_usage WHERE user_id=?", Integer.class, member))
                .isZero();
    }

    @Test
    @DisplayName("Authentication is required for both Sarah entry points")
    void bothAskEndpointsRequireAuthentication() {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<Map> workspace = restTemplate.exchange(url("/v1/cards/" + space + "/sarah/ask"), HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", UUID.randomUUID(), "question", "hello", "conversationHistory", List.of()), headers), Map.class);
        ResponseEntity<Map> contextual = restTemplate.exchange(url("/v1/resources/" + UUID.randomUUID() + "/sarah/ask"), HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", UUID.randomUUID(), "cardId", space, "question", "hello", "conversationHistory", List.of()), headers), Map.class);
        assertThat(workspace.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(contextual.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID createNote(UUID actor, UUID cardId, String title, String content) {
        ResponseEntity<Map> response = request(HttpMethod.POST, actor, "/v1/cards/" + cardId + "/notes",
                Map.of("title", title, "content", content), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID readyResource(UUID ownerCardId, String title, String text) {
        UUID resourceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO resource(id,owner_card_id,title,original_filename,mime_type,file_size_bytes,processing_status,extracted_content) VALUES (?,?,?,?,?,?, 'READY', ?::jsonb)",
                resourceId, ownerCardId, title, title + ".txt", "text/plain", text.length(), "[{\"text\":\"" + text + "\"}]");
        return resourceId;
    }

    private UUID user(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user(id,email,display_name) VALUES (?,?,?)", id,
                suffix + "-" + id + "@example.com", suffix);
        return id;
    }

    private UUID card(UUID ownerId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,false,0,false)",
                id, ownerId, name);
        return id;
    }

    private <T> ResponseEntity<T> request(HttpMethod method, UUID actor, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.toString());
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SarahRouterTestConfiguration {
        @Bean @Primary RecordingAiRouter recordingAiRouter() { return new RecordingAiRouter(); }
    }

    static class RecordingAiRouter implements AiRouter {
        private final List<SarahPrompt> prompts = new ArrayList<>();
        @Override public synchronized String answer(SarahPrompt prompt) { prompts.add(prompt); return "grounded answer"; }
        synchronized void clear() { prompts.clear(); }
        synchronized SarahPrompt lastPrompt() { return prompts.get(prompts.size() - 1); }
        synchronized int callCount() { return prompts.size(); }
    }
}
