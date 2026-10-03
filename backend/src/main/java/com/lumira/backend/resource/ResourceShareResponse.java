package com.lumira.backend.resource;

import java.time.Instant;
import java.util.UUID;

public record ResourceShareResponse(UUID id, String artifactType, UUID artifactId,
                                    UUID cardId, boolean active, Instant createdAt) {
    public static ResourceShareResponse from(ResourceShare share) {
        return new ResourceShareResponse(share.getId(), "resource", share.getResourceId(),
                share.getCardId(), share.isActive(), share.getCreatedAt());
    }
}
