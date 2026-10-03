package com.lumira.backend.card;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import com.lumira.backend.common.error.ValidationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for personal and shared Card endpoints (B1/B2 scope).
 *
 * <p>AD-019 API boundary:
 * <ul>
 *   <li>{@code POST /v1/cards} — create a personal Card
 *   <li>{@code GET /v1/cards} — list the authenticated user's own Cards
 *   <li>{@code GET /v1/cards/{id}} — fetch a single owned Card
 * </ul>
 *
 * <p>All endpoints require authentication ({@link RequireAuth}).
 * Reads revalidate ownership or active membership from persisted state.
 */
@RestController
@RequestMapping("/v1/cards")
@RequireAuth
public class CardController {

    private final CardService cardService;
    private final CourseSpaceService courseSpaceService;

    public CardController(CardService cardService, CourseSpaceService courseSpaceService) {
        this.cardService = cardService;
        this.courseSpaceService = courseSpaceService;
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
    public List<?> listMyCards(@RequestParam(required = false) String scope,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int pageSize,
                               @CurrentUser AuthenticatedUser currentUser) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw new ValidationException("page must be non-negative and pageSize must be between 1 and 100");
        }
        if (scope == null) {
            return cardService.listMyCards(currentUser.userId(), page, pageSize).stream().map(CardResponse::from).toList();
        }
        if ("shared".equals(scope)) return courseSpaceService.listSharedCards(currentUser.userId(), page, pageSize);
        throw new ValidationException("scope must be 'shared' when provided");
    }

    /**
     * Fetch a Card by id when owned or currently shared with the authenticated user;
     * absent and unauthorized Cards share the API's 404 response.
     */
    @GetMapping("/{id}")
    public CardResponse getMyCard(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return CardResponse.from(courseSpaceService.getAccessibleCard(id, currentUser.userId()));
    }
}
