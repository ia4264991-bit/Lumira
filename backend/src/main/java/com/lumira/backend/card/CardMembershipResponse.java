package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

public record CardMembershipResponse(UUID membershipId, UUID cardId, UUID userId,
                                     UUID memberCardId, String status, String role,
                                     Instant joinedAt) {
    static CardMembershipResponse from(CardMembership membership) {
        return new CardMembershipResponse(membership.getId(), membership.getCardId(),
                membership.getUserId(), membership.getMemberCardId(),
                membership.getStatus().name(), membership.getRole().name(),
                membership.getCreatedAt());
    }
}
