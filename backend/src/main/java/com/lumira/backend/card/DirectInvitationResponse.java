package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

public record DirectInvitationResponse(UUID membershipId, UUID cardId, UUID userId,
                                       UUID memberCardId, String status, String role,
                                       Instant createdAt) {
    static DirectInvitationResponse from(CardMembership membership) {
        return new DirectInvitationResponse(membership.getId(), membership.getCardId(),
                membership.getUserId(), membership.getMemberCardId(), membership.getStatus().name(),
                membership.getRole().name(), membership.getCreatedAt());
    }
}
