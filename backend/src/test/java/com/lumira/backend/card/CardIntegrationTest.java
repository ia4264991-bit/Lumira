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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1 integration tests: Identity + Personal Cards.
 *
 * <p>Validates:
 * <ul>
 *   <li>AD-048: a user may own multiple Cards (no one-primary-card limit)
 *   <li>AD-041/AD-056: Card ownership enforced — no cross-user access
 *   <li>AD-019: POST /v1/cards and GET /v1/cards exist and are scoped per owner
 *   <li>Auth boundary: unauthenticated requests are rejected
 * </ul>
 *
 * <p>Uses the {@link com.lumira.backend.security.FoundationTokenResolver}:
 * a valid UUID in the Bearer token resolves to that UUID as the userId.
 * Users must exist in the {@code app_user} table before Cards can be attributed.
 */
@DisplayName("B1 — Identity + Personal Cards")
class CardIntegrationTest extends BaseIntegrationTest {

    private UUID userAId;
    private UUID userBId;

    @BeforeEach
    void insertTestUsers() {
        // Clean state — order matters due to FK card → user
        jdbcTemplate.execute("TRUNCATE course_space_event, card_join_request, card_membership, card, app_user");

        userAId = UUID.randomUUID();
        userBId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO app_user (id, email, display_name) VALUES (?, ?, ?)",
                userAId, "alice@example.com", "Alice"
        );
        jdbcTemplate.update(
                "INSERT INTO app_user (id, email, display_name) VALUES (?, ?, ?)",
                userBId, "bob@example.com", "Bob"
        );
    }

    // -----------------------------------------------------------------------
    // Auth boundary
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/cards returns 401 without a token")
    void listCards_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/v1/cards"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /v1/cards returns 401 without a token")
    void createCard_unauthenticated_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"name\":\"My Card\"}", headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/v1/cards"), entity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // AD-048: a user may own MULTIPLE Cards
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AD-048: a user can create and list multiple Cards")
    void createAndListMultipleCards() {
        // Create three cards for user A
        createCard(userAId, "Operating Systems", "#FF5733");
        createCard(userAId, "Database Systems", "#33FF57");
        createCard(userAId, "My AI Notes", null);

        // List should return exactly those 3
        ResponseEntity<CardResponse[]> listResponse = getCards(userAId);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(3);

        // Names should include all three
        var names = java.util.Arrays.stream(listResponse.getBody())
                .map(CardResponse::name)
                .toList();
        assertThat(names).containsExactlyInAnyOrder("Operating Systems", "Database Systems", "My AI Notes");
    }

    @Test
    @DisplayName("AD-048: newly created Card has isShared=false by default")
    void newCard_isNotSharedByDefault() {
        CardResponse created = createCard(userAId, "My Private Card", null).getBody();

        assertThat(created).isNotNull();
        assertThat(created.isShared()).isFalse();
    }

    // -----------------------------------------------------------------------
    // AD-041/AD-056: cross-user access denied
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AD-056: user B cannot see user A's cards in their list")
    void listCards_doesNotReturnOtherUsersCards() {
        createCard(userAId, "Alice's Card", null);
        createCard(userBId, "Bob's Card", null);

        // User B should see only their own card
        ResponseEntity<CardResponse[]> listResponse = getCards(userBId);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].name()).isEqualTo("Bob's Card");
    }

    @Test
    @DisplayName("AD-056: user B gets 404 when fetching user A's card by id")
    void getCard_crossUserAccess_returns404() {
        CardResponse aliceCard = createCard(userAId, "Alice's Secret Card", null).getBody();
        assertThat(aliceCard).isNotNull();

        // Bob attempts to fetch Alice's card
        HttpHeaders headers = authHeaders(userBId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/v1/cards/" + aliceCard.id()), HttpMethod.GET, entity, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /v1/cards/{id} returns 404 for a non-existent card")
    void getCard_notFound_returns404() {
        HttpHeaders headers = authHeaders(userAId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/v1/cards/" + UUID.randomUUID()), HttpMethod.GET, entity, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/cards returns 400 when name is blank")
    void createCard_blankName_returns400() {
        HttpHeaders headers = authHeaders(userAId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"name\":\"   \"}", headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/v1/cards"), entity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ResponseEntity<CardResponse> createCard(UUID userId, String name, String color) {
        HttpHeaders headers = authHeaders(userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = color != null
                ? "{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}"
                : "{\"name\":\"" + name + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url("/v1/cards"), entity, CardResponse.class);
    }

    private ResponseEntity<CardResponse[]> getCards(UUID userId) {
        HttpHeaders headers = authHeaders(userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url("/v1/cards"), HttpMethod.GET, entity, CardResponse[].class);
    }

    private HttpHeaders authHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        // FoundationTokenResolver: a UUID bearer token resolves to that userId
        headers.setBearerAuth(userId.toString());
        return headers;
    }
}
