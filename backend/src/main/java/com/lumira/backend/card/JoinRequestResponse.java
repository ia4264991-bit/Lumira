package com.lumira.backend.card;

import java.time.Instant;
import java.util.UUID;

public record JoinRequestResponse(UUID joinRequestId, UUID cardId, UUID requestingUserId,
                                 String status, Instant createdAt, Instant resolvedAt,
                                 UUID resolvedByUserId) {
    static JoinRequestResponse from(CardJoinRequest request) {
        return new JoinRequestResponse(request.getId(), request.getCardId(),
                request.getRequestingUserId(), request.getStatus().name(),
                request.getCreatedAt(), request.getResolvedAt(), request.getResolvedByUserId());
    }
}
