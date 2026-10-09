package com.lumira.backend.study;

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
import java.time.Instant;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B5 — Notes and Study Sets")
class NotesStudySetsIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID other;
    private UUID privateCard;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE artifact_share, note, study_set, resource, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("b5-owner");
        other = user("b5-other");
        privateCard = card(owner, "Personal", false);
    }

    @Test
    @DisplayName("Note supports create, get, list, partial updates, owner-only deletion and persisted owner isolation")
    void noteCrudAndAuthorization() {
        ResponseEntity<Map> created = request(HttpMethod.POST, owner, "/v1/cards/" + privateCard + "/notes",
                Map.of("title", "Lecture", "content", "First draft"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(created.getBody().get("id").toString());
        assertThat(created.getBody()).containsKeys("ownerCardId", "ownerUserId", "createdAt", "updatedAt", "content");
        assertThat(created.getBody().get("ownerCardId").toString()).isEqualTo(privateCard.toString());
        assertThat(created.getBody().get("ownerUserId")).isNull();
        assertThat(request(HttpMethod.GET, owner, "/v1/notes/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards/" + privateCard + "/notes", null, Map[].class).getBody()).hasSize(1);

        ResponseEntity<Map> titleOnly = request(HttpMethod.PATCH, owner, "/v1/notes/" + id, Map.of("title", "Revised"), Map.class);
        assertThat(titleOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(titleOnly.getBody().get("title")).isEqualTo("Revised");
        assertThat(titleOnly.getBody().get("content")).isEqualTo("First draft");
        ResponseEntity<Map> contentOnly = request(HttpMethod.PATCH, owner, "/v1/notes/" + id, Map.of("content", "Final"), Map.class);
        assertThat(contentOnly.getBody().get("title")).isEqualTo("Revised");
        assertThat(contentOnly.getBody().get("content")).isEqualTo("Final");

        assertThat(request(HttpMethod.GET, other, "/v1/notes/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, other, "/v1/notes/" + id, Map.of("title", "Intrusion"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, other, "/v1/notes/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/notes/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(HttpMethod.GET, owner, "/v1/notes/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE note_id=?", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("StudySet supports create, get, list, partial updates and owner-only deletion")
    void studySetCrudAndAuthorization() {
        ResponseEntity<Map> created = request(HttpMethod.POST, owner, "/v1/cards/" + privateCard + "/studysets",
                Map.of("title", "Biology", "description", "Cell concepts"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(created.getBody().get("id").toString());
        assertThat(created.getBody()).containsKeys("ownerCardId", "ownerUserId", "description", "createdAt", "updatedAt");
        assertThat(created.getBody().get("ownerUserId")).isNull();
        assertThat(request(HttpMethod.GET, owner, "/v1/studysets/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards/" + privateCard + "/studysets", null, Map[].class).getBody()).hasSize(1);

        ResponseEntity<Map> titleOnly = request(HttpMethod.PATCH, owner, "/v1/studysets/" + id, Map.of("title", "Anatomy"), Map.class);
        assertThat(titleOnly.getBody().get("title")).isEqualTo("Anatomy");
        assertThat(titleOnly.getBody().get("description")).isEqualTo("Cell concepts");
        ResponseEntity<Map> descriptionOnly = request(HttpMethod.PATCH, owner, "/v1/studysets/" + id,
                Map.of("description", "Updated concepts"), Map.class);
        assertThat(descriptionOnly.getBody().get("title")).isEqualTo("Anatomy");
        assertThat(descriptionOnly.getBody().get("description")).isEqualTo("Updated concepts");

        assertThat(request(HttpMethod.GET, other, "/v1/studysets/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.PATCH, other, "/v1/studysets/" + id, Map.of("title", "Intrusion"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, other, "/v1/studysets/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/studysets/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(HttpMethod.GET, owner, "/v1/studysets/" + id, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE study_set_id=?", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("Notes and Study Sets on a Course Space remain private until explicitly shared")
    void courseSpaceArtifactsRemainPrivateByDefault() {
        UUID space = card(owner, "Shared workspace", false);
        request(HttpMethod.POST, owner, "/v1/cards/" + space + "/share", null, Map.class);
        UUID memberCard = card(other, "Member workspace", false);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')",
                space, other, memberCard);

        Map<String, Object> createdNote = request(HttpMethod.POST, owner, "/v1/cards/" + space + "/notes",
                Map.of("title", "Private note", "content", "Only the owner should see this"), Map.class).getBody();
        Map<String, Object> createdSet = request(HttpMethod.POST, owner, "/v1/cards/" + space + "/studysets",
                Map.of("title", "Private set", "description", "Only the owner should see this"), Map.class).getBody();
        UUID noteId = UUID.fromString(createdNote.get("id").toString());
        UUID setId = UUID.fromString(createdSet.get("id").toString());

        assertThat(createdNote.get("sharedWithThisCourseSpace")).isEqualTo(false);
        assertThat(createdSet.get("sharedWithThisCourseSpace")).isEqualTo(false);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards/" + space + "/notes", null, Map[].class).getBody()).hasSize(1);
        assertThat(request(HttpMethod.GET, owner, "/v1/cards/" + space + "/studysets", null, Map[].class).getBody()).hasSize(1);
        assertThat(request(HttpMethod.GET, other, "/v1/cards/" + space + "/notes", null, Map[].class).getBody()).isEmpty();
        assertThat(request(HttpMethod.GET, other, "/v1/cards/" + space + "/studysets", null, Map[].class).getBody()).isEmpty();
        assertThat(request(HttpMethod.POST, other, "/v1/cards/" + space + "/notes",
                Map.of("title", "Wrong Card", "content", "private"), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE note_id=? AND active", Integer.class, noteId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE study_set_id=? AND active", Integer.class, setId)).isZero();

        request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/share", Map.of("cardId", space), Map.class);
        Map[] sharedNotes = request(HttpMethod.GET, other, "/v1/cards/" + space + "/notes", null, Map[].class).getBody();
        assertThat(sharedNotes).hasSize(1);
        assertThat(sharedNotes[0].get("sharedWithThisCourseSpace")).isEqualTo(true);
    }

    @Test
    @DisplayName("Note and StudySet use the same active Course Space share mechanism without ownership changes")
    void sharedArtifactsUseLiveMembershipAndUnshare() {
        UUID space = card(owner, "Course Space", false);
        assertThat(request(HttpMethod.POST, owner, "/v1/cards/" + space + "/share", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID memberCard = card(other, "Member Card", false);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')", space, other, memberCard);

        UUID noteId = UUID.fromString(request(HttpMethod.POST, owner, "/v1/cards/" + privateCard + "/notes",
                Map.of("title", "Shared note", "content", "Visible"), Map.class).getBody().get("id").toString());
        UUID setId = UUID.fromString(request(HttpMethod.POST, owner, "/v1/cards/" + privateCard + "/studysets",
                Map.of("title", "Shared set", "description", "Visible"), Map.class).getBody().get("id").toString());
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/studyset/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.GET, other, "/v1/notes/" + noteId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, other, "/v1/studysets/" + setId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, other, "/v1/cards/" + space + "/notes", null, Map[].class).getBody()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id=? AND type='ARTIFACT_SHARED'", Integer.class, space)).isEqualTo(2);

        assertThat(request(HttpMethod.DELETE, owner, "/v1/artifacts/note/" + noteId + "/share/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/studyset/" + setId + "/force-unshare/" + space,
                null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, other, "/v1/notes/" + noteId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET, other, "/v1/studysets/" + setId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM note WHERE id=?", UUID.class, noteId)).isEqualTo(privateCard);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM study_set WHERE id=?", UUID.class, setId)).isEqualTo(privateCard);
        assertThat(jdbcTemplate.queryForObject("SELECT payload->>'reason' FROM course_space_event WHERE card_id=? AND type='CONTENT_FORCE_UNSHARED'", String.class, space)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id=? AND type='CONTENT_UNSHARED'", Integer.class, space)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM note WHERE id=?", Integer.class, noteId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM study_set WHERE id=?", Integer.class, setId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE note_id=? AND active", Integer.class, noteId)).isZero();
        assertThat(request(HttpMethod.POST, other, "/v1/cards/" + space + "/notes",
                Map.of("title", "Denied", "content", "No write access"), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/note/" + noteId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/notes/" + noteId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE note_id=?", Integer.class, noteId)).isZero();
        assertThat(request(HttpMethod.POST, owner, "/v1/artifacts/studyset/" + setId + "/share",
                Map.of("cardId", space), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.DELETE, owner, "/v1/studysets/" + setId, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE study_set_id=?", Integer.class, setId)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL enforces Note and StudySet owner XOR and real foreign keys; no type forks or child model")
    void ownershipConstraintsAndCanonicalTypes() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO note(owner_card_id,owner_user_id,title,content) VALUES (?,?,?,?)", privateCard, owner, "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO note(owner_card_id,owner_user_id,title,content) VALUES (NULL,NULL,?,?)", "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO note(owner_card_id,title,content) VALUES (?,?,?)", UUID.randomUUID(), "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO study_set(owner_card_id,owner_user_id,title,description) VALUES (?,?,?,?)", privateCard, owner, "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO study_set(owner_card_id,owner_user_id,title,description) VALUES (NULL,NULL,?,?)", "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO study_set(owner_card_id,title,description) VALUES (?,?,?)", UUID.randomUUID(), "bad", "bad"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('personal_note') IS NULL AND to_regclass('course_space_note') IS NULL AND to_regclass('personal_study_set') IS NULL AND to_regclass('course_space_study_set') IS NULL AND to_regclass('study_set_item') IS NULL", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("Account deletion applies the active-share successor rule to Note and StudySet")
    void sharedNoteAndStudySetSurviveAccountDeletion() {
        UUID spaceOwnerA = user("space-a");
        UUID spaceOwnerB = user("space-b");
        UUID spaceA = card(spaceOwnerA, "Space A", false);
        UUID spaceB = card(spaceOwnerB, "Space B", false);
        assertThat(request(HttpMethod.POST, spaceOwnerA, "/v1/cards/" + spaceA + "/share", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST, spaceOwnerB, "/v1/cards/" + spaceB + "/share", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID noteMemberCard = card(owner, "note member", false);
        UUID setMemberCard = card(owner, "set member", false);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')", spaceA, owner, noteMemberCard);
        jdbcTemplate.update("INSERT INTO card_membership(card_id,user_id,member_card_id,status,role) VALUES (?,?,?,'ACTIVE','MEMBER')", spaceB, owner, setMemberCard);

        UUID noteId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO note(id,owner_user_id,title,content) VALUES (?,?,?,?)", noteId, owner, "Shared note", "body");
        jdbcTemplate.update("INSERT INTO study_set(id,owner_user_id,title,description) VALUES (?,?,?,?)", setId, owner, "Shared set", "desc");
        Instant oldest = Instant.parse("2026-01-01T00:00:00Z");
        shareNote(noteId, spaceB, oldest.plusSeconds(5));
        shareNote(noteId, spaceA, oldest);
        shareStudySet(setId, spaceB, oldest.plusSeconds(5));
        shareStudySet(setId, spaceA, oldest);

        assertThat(request(HttpMethod.DELETE, owner, "/v1/me", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM note WHERE id=?", UUID.class, noteId)).isEqualTo(spaceA);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM note WHERE id=?", UUID.class, noteId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT owner_card_id FROM study_set WHERE id=?", UUID.class, setId)).isEqualTo(spaceA);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_user_id FROM study_set WHERE id=?", UUID.class, setId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE note_id=? AND active", Integer.class, noteId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM artifact_share WHERE study_set_id=? AND active", Integer.class, setId)).isEqualTo(2);
    }

    private void shareNote(UUID noteId, UUID cardId, Instant createdAt) {
        jdbcTemplate.update("INSERT INTO artifact_share(note_id,card_id,active,created_at) VALUES (?,?,true,?)",
                noteId, cardId, Timestamp.from(createdAt));
    }

    private void shareStudySet(UUID setId, UUID cardId, Instant createdAt) {
        jdbcTemplate.update("INSERT INTO artifact_share(study_set_id,card_id,active,created_at) VALUES (?,?,true,?)",
                setId, cardId, Timestamp.from(createdAt));
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
        jdbcTemplate.update("INSERT INTO card(id,owner_id,name,is_shared,invite_token_version,require_approval) VALUES (?,?,?,?,0,false)", id, ownerId, name, shared);
        return id;
    }
}
