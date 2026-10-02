package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

public record SharedCardResponse(UUID id, UUID ownerId, String name, String color,
                                 boolean isShared, String role, Instant createdAt) {
    static SharedCardResponse from(Card card, MembershipRole role) {
        return new SharedCardResponse(card.getId(), card.getOwnerId(), card.getName(),
                card.getColor(), card.isShared(), role.name(), card.getCreatedAt());
    }
}
