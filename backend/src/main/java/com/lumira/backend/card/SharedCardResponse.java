package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

public record SharedCardResponse(UUID id, UUID ownerId, String name, String color,
                                 boolean isShared, String role, UUID memberCardId, Instant createdAt) {
    static SharedCardResponse from(Card card, MembershipRole role, UUID memberCardId) {
        return new SharedCardResponse(card.getId(), card.getOwnerId(), card.getName(),
                card.getColor(), card.isShared(), role.name(), memberCardId, card.getCreatedAt());
    }
}
