package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Card endpoints.
 *
 * <p>Per AD-019 persistence shape: {@code id, ownerId, name, color, isShared, createdAt}.
 */
public record CardResponse(
        UUID id,
        UUID ownerId,
        String name,
        String color,
        boolean isShared,
        Instant createdAt
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getOwnerId(),
                card.getName(),
                card.getColor(),
                card.isShared(),
                card.getCreatedAt()
        );
    }
}
