package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Card endpoints (B1 scope).
 *
 * <p>AD-019 API boundary:
 * <ul>
 *   <li>{@code POST /v1/cards} — create a personal Card
 *   <li>{@code GET /v1/cards} — list the authenticated user's own Cards
 *   <li>{@code GET /v1/cards/{id}} — fetch a single owned Card
 * </ul>
 *
 * <p>All endpoints require authentication ({@link RequireAuth}).
 * Responses are owner-scoped — no cross-user data is ever returned.
 */
@RestController
@RequestMapping("/v1/cards")
@RequireAuth
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    /**
     * Create a new personal Card for the authenticated user.
     * AD-048: users may own multiple cards; no uniqueness constraint on name.
     */
    @PostMapping
    public ResponseEntity<CardResponse> createCard(
            @Valid @RequestBody CreateCardRequest request,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        Card card = cardService.createCard(currentUser.userId(), request.name(), request.color());
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    /**
     * List the authenticated user's Cards, newest first.
     */
    @GetMapping
    public List<CardResponse> listMyCards(@CurrentUser AuthenticatedUser currentUser) {
        return cardService.listMyCards(currentUser.userId())
                .stream()
                .map(CardResponse::from)
                .toList();
    }

    /**
     * Fetch a single Card by id — only if owned by the authenticated user.
     */
    @GetMapping("/{id}")
    public CardResponse getMyCard(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return CardResponse.from(cardService.getMyCard(id, currentUser.userId()));
    }
}
