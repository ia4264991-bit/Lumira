package com.lumira.backend.quiz;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B6 — Quizzes and private attempts")
class QuizIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID member;
    private UUID other;
    private UUID spaceOwner;
    private UUID personalCard;
    private UUID space;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE quiz_attempt_answer_option, quiz_attempt_answer, quiz_attempt, quiz_question_option, quiz_question, quiz, artifact_share, resource, note, study_set, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("b6-owner");
        member = user("b6-member");
        other = user("b6-other");
        spaceOwner = user("b6-space-owner");
        personalCard = card(owner, "Personal", false);
        space = card(spaceOwner, "Course Space", false);
        assertThat(request(HttpMethod.POST, spaceOwner, "/v1/cards/" + space + "/share", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID ownerMemberCard = card(owner, "Owner member Card", false);
        UUID memberCard = card(member, "Member Card", false);
        UUID otherCard = card(other, "Other Card", false);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, owner, ownerMemberCard);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, member, memberCard);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, other, otherCard);
    }

    @Test
    @DisplayName("Quiz CRUD is owner-authorized, shared reads hide the answer key, and invalid creation is atomic")
    void quizCrudSharingAndValidation() {
        ResponseEntity<Map> created = request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/quizzes", quizBody("Cell Biology"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID quizId = UUID.fromString(created.getBody().get("id").toString());
        assertThat(created.getBody().get("ownerCardId").toString()).isEqualTo(personalCard.toString());
        Map<String, Object> ownQuestion = (Map<String, Object>) ((List<?>) created.getBody().get("questions")).getFirst();
        assertThat(((List<Map<String, Object>>) ownQuestion.get("options")).getFirst().get("correct")).isEqualTo(true);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards/" + personalCard + "/quizzes", null, Map[].class).getBody()).hasSize(1);
        assertThat(request(HttpMethod.GET, other, "/v1/quizzes/" + quizId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, other, "/v1/quizzes/" + quizId, Map.of("title", "Takeover"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, other, "/v1/quizzes/" + quizId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> invalid = Map.of("title", "Invalid", "description", "Description",
                "questions", List.of(Map.of("position", 1, "prompt", "Question",
                        "options", List.of(Map.of("position", 1, "text", "A", "correct", true),
                                Map.of("position", 2, "text", "B", "correct", true)))));
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/quizzes", invalid, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz", Integer.class)).isEqualTo(1);

        ResponseEntity<Map> share = request(HttpMethod.POST, owner, "/v1/artifacts/quiz/" + quizId + "/share",
                Map.of("cardId", space), Map.class);
        assertThat(share.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> sharedRead = request(HttpMethod.GET, member, "/v1/quizzes/" + quizId, null, Map.class);
        assertThat(sharedRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, member, "/v1/cards/" + space + "/quizzes", null, Map[].class).getBody()).hasSize(1);
        Map<String, Object> sharedQuestion = (Map<String, Object>) ((List<?>) sharedRead.getBody().get("questions")).getFirst();
        List<Map<String, Object>> sharedOptions = (List<Map<String, Object>>) sharedQuestion.get("options");
        assertThat(sharedOptions).allSatisfy(option -> assertThat(option.get("correct")).isNull());

        assertThat(request(HttpMethod.DELETE, owner, "/v1/artifacts/quiz/" + quizId + "/share/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, member, "/v1/quizzes/" + quizId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz WHERE id=?", Integer.class, quizId)).isEqualTo(1);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/quizzes/" + quizId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE quiz_id=?", Integer.class, quizId)).isZero();
    }

    @Test
    @DisplayName("Course Space Quizzes are private until explicit sharing and members create on their linked Card")
    void courseSpaceQuizPrivacyAndMemberCardOwnership() {
        ResponseEntity<Map> created = request(HttpMethod.POST, spaceOwner,
                "/v1/cards/" + space + "/quizzes", quizBody("Owner private quiz"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID quizId = UUID.fromString(created.getBody().get("id").toString());
        assertThat(created.getBody().get("sharedWithThisCourseSpace")).isEqualTo(false);
        assertThat(request(HttpMethod.GET, member, "/v1/cards/" + space + "/quizzes", null, Map[].class).getBody()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE quiz_id=? AND active",
                Integer.class, quizId)).isZero();
        assertThat(request(HttpMethod.POST, member, "/v1/cards/" + space + "/quizzes",
                quizBody("Should use member Card"), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        request(HttpMethod.POST, spaceOwner, "/v1/artifacts/quiz/" + quizId + "/share", Map.of("cardId", space), Map.class);
        Map[] shared = request(HttpMethod.GET, member, "/v1/cards/" + space + "/quizzes", null, Map[].class).getBody();
        assertThat(shared).hasSize(1);
        assertThat(shared[0].get("sharedWithThisCourseSpace")).isEqualTo(true);
    }

    @Test
    @DisplayName("Shared quiz attempts are independently scored and remain readable after canonical edits")
    void attemptsArePrivateServerScoredAndSnapshotContent() {
        UUID quizId = createSharedQuiz();
        Map<String, Object> currentQuiz = request(HttpMethod.GET, member, "/v1/quizzes/" + quizId, null, Map.class).getBody();
        List<Map<String, Object>> questionRows = (List<Map<String, Object>>) currentQuiz.get("questions");
        List<Map<String, Object>> userAnswers = new ArrayList<>();
        List<Map<String, Object>> otherAnswers = new ArrayList<>();
        for (Map<String, Object> q : questionRows) {
            List<Map<String, Object>> options = (List<Map<String, Object>>) q.get("options");
            UUID qid = UUID.fromString(q.get("id").toString());
            UUID correct = UUID.fromString(options.get(0).get("id").toString());
            UUID incorrect = UUID.fromString(options.get(1).get("id").toString());
            userAnswers.add(Map.of("questionId", qid, "optionId", correct));
            otherAnswers.add(Map.of("questionId", qid, "optionId", incorrect));
        }
        Map<String, Object> clientAttempt = Map.of("answers", userAnswers, "correctCount", 0, "score", 0);
        ResponseEntity<Map> attemptA = request(HttpMethod.POST, member, "/v1/quizzes/" + quizId + "/attempts", clientAttempt, Map.class);
        ResponseEntity<Map> attemptB = request(HttpMethod.POST, other, "/v1/quizzes/" + quizId + "/attempts",
                Map.of("answers", otherAnswers), Map.class);
        assertThat(attemptA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(attemptA.getBody().get("correctCount")).isEqualTo(2);
        assertThat(attemptB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(attemptB.getBody().get("correctCount")).isEqualTo(0);
        assertThat(attemptA.getBody().get("userId").toString()).isEqualTo(member.toString());
        assertThat(attemptB.getBody().get("userId").toString()).isEqualTo(other.toString());
        ResponseEntity<Map> repeatAttempt = request(HttpMethod.POST, member, "/v1/quizzes/" + quizId + "/attempts",
                Map.of("answers", otherAnswers), Map.class);
        assertThat(repeatAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeatAttempt.getBody().get("correctCount")).isEqualTo(0);
        assertThat(repeatAttempt.getBody().get("id")).isNotEqualTo(attemptA.getBody().get("id"));
        assertThat(((List<?>) request(HttpMethod.GET, member, "/v1/quizzes/" + quizId + "/attempts/me", null, List.class).getBody())).hasSize(2);
        assertThat(((List<?>) request(HttpMethod.GET, other, "/v1/quizzes/" + quizId + "/attempts/me", null, List.class).getBody())).hasSize(1);
        assertThat(request(HttpMethod.GET, member, "/v1/quizzes/" + quizId + "/attempts/" + attemptB.getBody().get("id"), null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<Map<String, Object>> editedQuestions = new ArrayList<>();
        for (Map<String, Object> q : questionRows) {
            List<Map<String, Object>> opts = (List<Map<String, Object>>) q.get("options");
            List<Map<String, Object>> editOpts = new ArrayList<>();
            for (int i = 0; i < opts.size(); i++) {
                Map<String, Object> option = opts.get(i);
                editOpts.add(Map.of("id", option.get("id"), "position", i + 1,
                        "text", "Changed " + option.get("text"), "correct", i == 0));
            }
            editedQuestions.add(Map.of("id", q.get("id"), "position", q.get("position"),
                    "prompt", "Changed " + q.get("prompt"), "options", editOpts));
        }
        assertThat(request(HttpMethod.PATCH, owner, "/v1/quizzes/" + quizId,
                Map.of("questions", editedQuestions), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> mine = (Map<String, Object>) ((List<?>) request(HttpMethod.GET, member,
                "/v1/quizzes/" + quizId + "/attempts/me", null, List.class).getBody()).getFirst();
        Map<String, Object> answerSnapshot = (Map<String, Object>) ((List<?>) mine.get("answers")).getFirst();
        assertThat(answerSnapshot.get("prompt")).isEqualTo(questionRows.getFirst().get("prompt"));
        assertThat(request(HttpMethod.GET, owner, "/v1/quizzes/" + quizId, null, Map.class).getBody()
                .get("questions").toString()).contains("Changed");

        assertThat(request(HttpMethod.DELETE, owner, "/v1/artifacts/quiz/" + quizId + "/share/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST, member, "/v1/quizzes/" + quizId + "/attempts",
                Map.of("answers", userAnswers), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PostgreSQL enforces Quiz ownership, share XOR, question ordering and correct-option integrity")
    void databaseConstraintsProtectQuizStructure() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO quiz(owner_card_id,owner_user_id,title,description) VALUES (?,?,?,?)",
                personalCard, owner, "invalid", "invalid")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO quiz(owner_card_id,title,description) VALUES (?,?,?)",
                UUID.randomUUID(), "invalid", "invalid")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO quiz(title,description) VALUES (?,?)", "invalid", "invalid"))
                .isInstanceOf(RuntimeException.class);
        UUID quizId = UUID.fromString(request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/quizzes",
                quizBody("Valid"), Map.class).getBody().get("id").toString());
        UUID questionId = UUID.fromString(jdbcTemplate.queryForObject("SELECT id FROM quiz_question WHERE quiz_id=? ORDER BY position LIMIT 1", String.class, quizId));
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO quiz_question_option(question_id,position,option_text,is_correct) VALUES (?,?,?,true)",
                questionId, 9, "second correct")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE quiz_question_option SET is_correct=false WHERE question_id=?", questionId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO artifact_share(quiz_id,resource_id,card_id,active) VALUES (?,?,?,true)",
                quizId, UUID.randomUUID(), space)).isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz_attempt WHERE quiz_id=?", Integer.class, quizId)).isZero();
    }

    @Test
    @DisplayName("Account deletion transfers a shared Quiz to the active Course Space and removes only the deleting user's attempts")
    void accountDeletionPreservesSharedQuizAndOtherAttempts() {
        UUID quizId = UUID.fromString(request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/quizzes",
                quizBody("Survives"), Map.class).getBody().get("id").toString());
        request(HttpMethod.POST, owner, "/v1/artifacts/quiz/" + quizId + "/share", Map.of("cardId", space), Map.class);
        List<Map<String, Object>> qs = (List<Map<String, Object>>) request(HttpMethod.GET, member,
                "/v1/quizzes/" + quizId, null, Map.class).getBody().get("questions");
        List<Map<String, Object>> ans = qs.stream().map(q -> Map.of("questionId", q.get("id"),
                "optionId", ((List<Map<String, Object>>) q.get("options")).getFirst().get("id"))).toList();
        request(HttpMethod.POST, member, "/v1/quizzes/" + quizId + "/attempts", Map.of("answers", ans), Map.class);
        request(HttpMethod.POST, owner, "/v1/quizzes/" + quizId + "/attempts", Map.of("answers", ans), Map.class);

        assertThat(request(HttpMethod.DELETE, owner, "/v1/me", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM quiz WHERE id=?", UUID.class, quizId)).isEqualTo(space);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM quiz WHERE id=?", UUID.class, quizId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE quiz_id=? AND active", Integer.class, quizId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz_attempt WHERE quiz_id=?", Integer.class, quizId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM quiz_attempt_answer_option", Integer.class)).isEqualTo(4);
    }

    private UUID createSharedQuiz() {
        UUID quizId = UUID.fromString(request(HttpMethod.POST, owner, "/v1/cards/" + personalCard + "/quizzes",
                quizBody("Shared quiz"), Map.class).getBody().get("id").toString());
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/quiz/" + quizId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return quizId;
    }

    private Map<String, Object> quizBody(String title) {
        return Map.of("title", title, "description", "A concise quiz",
                "questions", List.of(question(1, "Question one", "A1"), question(2, "Question two", "A2")));
    }

    private Map<String, Object> question(int position, String prompt, String correctText) {
        return Map.of("position", position, "prompt", prompt, "options", List.of(
                Map.of("position", 1, "text", correctText, "correct", true),
                Map.of("position", 2, "text", "Wrong " + position, "correct", false)));
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
