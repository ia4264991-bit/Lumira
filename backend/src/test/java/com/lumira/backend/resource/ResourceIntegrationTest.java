package com.lumira.backend.resource;

import com.lumira.backend.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B3 â€” Resources")
class ResourceIntegrationTest extends BaseIntegrationTest {
    private UUID owner;
    private UUID other;
    private UUID card;

    @Autowired private ResourceService resourceService;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE artifact_share, resource, course_space_event, card_join_request, card_membership, card, app_user CASCADE");
        owner = user("resource-owner@example.com");
        other = user("resource-other@example.com");
        card = card(owner, "Private resources", false);
    }

    @Test
    @DisplayName("Personal Card upload extracts valid TXT and reprocessing retains the same row")
    void personalCardUploadAndReprocess() {
        ResponseEntity<Map> uploaded = upload(card, owner, "lesson.txt", "text/plain", "first line\nsecond line");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map body = uploaded.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("READY");
        assertThat(body.get("ownerCardId").toString()).isEqualTo(card.toString());
        UUID resourceId = UUID.fromString(body.get("id").toString());
        assertThat(((Number) jdbcTemplate.queryForObject("SELECT count(*) FROM resource", Number.class)).intValue()).isEqualTo(1);

        ResponseEntity<Map> rerun = restTemplate.exchange(url("/v1/resources/" + resourceId + "/reprocess"),
                HttpMethod.POST, new HttpEntity<>(auth(owner)), Map.class);
        assertThat(rerun.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rerun.getBody().get("id").toString()).isEqualTo(resourceId.toString());
        assertThat(rerun.getBody().get("status")).isEqualTo("READY");
        assertThat(((Number) jdbcTemplate.queryForObject("SELECT count(*) FROM resource", Number.class)).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("CSV upload preserves row/column information")
    void csvReachesReadyWithLocations() {
        ResponseEntity<Map> response = upload(card, owner, "grades.csv", "text/csv", "name,score\nAda,98\nLin,91");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("READY");
        assertThat(response.getBody().get("extractedContent").toString()).contains("Ada", "row", "score");
    }

    @Test
    @DisplayName("Valid and password-protected PDFs are processed or rejected with clear state")
    void pdfReadyAndEncryptedPdfFailed() throws Exception {
        byte[] validPdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText("Lumira resource extraction");
                stream.endText();
            }
            document.save(out);
            validPdf = out.toByteArray();
        }
        ResponseEntity<Map> ready = uploadBytes(card, owner, "notes.pdf", "application/pdf", validPdf);
        assertThat(ready.getBody().get("status")).isEqualTo("READY");
        assertThat(ready.getBody().get("extractedContent").toString()).contains("Lumira resource extraction", "page");

        byte[] encrypted;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()));
            document.save(out);
            encrypted = out.toByteArray();
        }
        ResponseEntity<Map> rejected = uploadBytes(card, owner, "locked.pdf", "application/pdf", encrypted);
        assertThat(rejected.getBody().get("status")).isEqualTo("FAILED");
        assertThat(rejected.getBody().get("failureReason").toString()).containsIgnoringCase("password");
    }

    @Test
    @DisplayName("unsupported, mismatched and malformed files persist FAILED with a human reason")
    void invalidUploadsAreStoredAsFailed() {
        ResponseEntity<Map> unsupported = upload(card, owner, "legacy.doc", "application/msword", "legacy");
        ResponseEntity<Map> mismatch = upload(card, owner, "wrong.txt", "text/plain", "%PDF-1.7\nnot a pdf");
        ResponseEntity<Map> malformed = upload(card, owner, "broken.pdf", "application/pdf", "%PDF-1.7\nnot a PDF document");
        for (ResponseEntity<Map> response : new ResponseEntity[]{unsupported, mismatch, malformed}) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().get("status")).isEqualTo("FAILED");
            assertThat(response.getBody().get("failureReason").toString()).isNotBlank();
        }
        assertThat(unsupported.getBody().get("failureReason").toString()).containsIgnoringCase("not supported");
        assertThat(mismatch.getBody().get("failureReason").toString()).containsIgnoringCase("signature");
    }

    @Test
    @DisplayName("User-owned Resource domain branch is persisted with exactly one User owner")
    void directUserOwnershipIsSupported() {
        var file = new MockMultipartFile("file", "orphan.txt", "text/plain", "unorganized notes".getBytes());
        ResourceResponse response = resourceService.createForUser(owner, file, "Unorganized notes");
        assertThat(response.ownerUserId()).isEqualTo(owner);
        assertThat(response.ownerCardId()).isNull();
        assertThat(response.status()).isEqualTo(ResourceStatus.READY);
        assertThat(jdbcTemplate.queryForObject("SELECT num_nonnulls(owner_card_id, owner_user_id) FROM resource WHERE id = ?", Integer.class, response.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Cross-user private Card upload, Resource retrieval and reprocess are denied without revealing existence")
    void privateCardIdorIsDenied() {
        ResponseEntity<Map> created = upload(card, owner, "private.txt", "text/plain", "private text");
        UUID resourceId = UUID.fromString(created.getBody().get("id").toString());
        assertThat(upload(card, other, "attempt.txt", "text/plain", "attempt").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(url("/v1/resources/" + resourceId), HttpMethod.GET, new HttpEntity<>(auth(other)), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(url("/v1/resources/" + resourceId + "/reprocess"), HttpMethod.POST,
                new HttpEntity<>(auth(other)), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Course Space Owner/Admin can upload, active Member can retrieve, and a removed user cannot")
    void courseSpaceUsesLiveRolesAndShareState() {
        enableSharing(card, owner);
        UUID admin = user("resource-admin@example.com");
        UUID member = user("resource-member@example.com");
        UUID removed = user("resource-removed@example.com");
        addMembership(card, admin, "ADMIN");
        addMembership(card, member, "MEMBER");
        addMembership(card, removed, "MEMBER");
        jdbcTemplate.update("UPDATE card_membership SET status='REMOVED' WHERE card_id=? AND user_id=?", card, removed);

        ResponseEntity<Map> deniedMemberUpload = upload(card, member, "member.txt", "text/plain", "not allowed");
        assertThat(deniedMemberUpload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> ownerUpload = upload(card, owner, "owner.txt", "text/plain", "owner course notes");
        assertThat(ownerUpload.getBody().get("status")).isEqualTo("READY");
        ResponseEntity<Map> uploaded = upload(card, admin, "shared.txt", "text/plain", "shared course notes");
        assertThat(uploaded.getBody().get("status")).isEqualTo("READY");
        UUID resourceId = UUID.fromString(uploaded.getBody().get("id").toString());
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM artifact_share WHERE resource_id = ? AND card_id = ?", Boolean.class, resourceId, card)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM course_space_event WHERE card_id = ? AND type = 'RESOURCE_ADDED'", Integer.class, card)).isEqualTo(2);
        ResourceResponse[] listed = restTemplate.exchange(url("/v1/cards/" + card + "/resources"), HttpMethod.GET,
                new HttpEntity<>(auth(member)), ResourceResponse[].class).getBody();
        assertThat(listed).hasSize(2);
        ResponseEntity<byte[]> visible = restTemplate.exchange(url("/v1/resources/" + resourceId), HttpMethod.GET,
                new HttpEntity<>(auth(member)), byte[].class);
        assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(visible.getBody())).isEqualTo("shared course notes");
        assertThat(restTemplate.exchange(url("/v1/resources/" + resourceId), HttpMethod.GET,
                new HttpEntity<>(auth(removed)), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        jdbcTemplate.update("UPDATE card_membership SET status='REMOVED' WHERE card_id=? AND user_id=?", card, member);
        assertThat(restTemplate.exchange(url("/v1/resources/" + resourceId), HttpMethod.GET,
                new HttpEntity<>(auth(member)), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PostgreSQL XOR check rejects both and neither ownership foreign key")
    void databaseEnforcesXorOwner() {
        String sql = "INSERT INTO resource (owner_card_id, owner_user_id, title, original_filename, mime_type, file_size_bytes, processing_status, failure_reason) VALUES (?, ?, 'bad', 'bad.txt', 'text/plain', 0, 'FAILED', 'invalid')";
        assertThatThrownBy(() -> jdbcTemplate.update(sql, card, owner)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(sql, null, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("GET requires authentication")
    void retrievalRequiresAuthentication() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/v1/cards/" + card + "/resources"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void enableSharing(UUID cardId, UUID ownerId) {
        assertThat(restTemplate.postForEntity(url("/v1/cards/" + cardId + "/share"),
                new HttpEntity<>(auth(ownerId)), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> upload(UUID cardId, UUID actor, String filename, String mime, String body) {
        return uploadBytes(cardId, actor, filename, mime, body.getBytes());
    }

    private ResponseEntity<Map> uploadBytes(UUID cardId, UUID actor, String filename, String mime, byte[] content) {
        HttpHeaders headers = auth(actor);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(mime));
        parts.add("file", new HttpEntity<>(new NamedBytes(content, filename), partHeaders));
        parts.add("title", filename);
        return restTemplate.postForEntity(url("/v1/cards/" + cardId + "/resources"), new HttpEntity<>(parts, headers), Map.class);
    }

    private UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, display_name) VALUES (?, ?, ?)", id, email, email);
        return id;
    }

    private UUID card(UUID ownerId, String name, boolean shared) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO card (id, owner_id, name, is_shared, invite_token_version, require_approval) VALUES (?, ?, ?, ?, 0, false)", id, ownerId, name, shared);
        return id;
    }

    private void addMembership(UUID courseCard, UUID userId, String role) {
        UUID memberCard = card(userId, "member card", false);
        jdbcTemplate.update("INSERT INTO card_membership (card_id, user_id, member_card_id, status, role) VALUES (?, ?, ?, 'ACTIVE', ?)", courseCard, userId, memberCard, role);
    }

    private HttpHeaders auth(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userId.toString());
        return headers;
    }

    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;
        NamedBytes(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
