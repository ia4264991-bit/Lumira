package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
                               String originalFilename, String mimeType, long fileSizeBytes,
                               ResourceStatus status, String failureReason,
                               JsonNode extractedContent, JsonNode imageMetadata, Instant createdAt) {
    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getOwner().getOwningCardId(),
                resource.getOwner().getOwningUserId(), resource.getTitle(), resource.getOriginalFilename(),
                resource.getMimeType(), resource.getFileSizeBytes(), resource.getStatus(),
                resource.getFailureReason(), resource.getExtractedContent(), resource.getImageMetadata(),
                resource.getCreatedAt());
    }
}
