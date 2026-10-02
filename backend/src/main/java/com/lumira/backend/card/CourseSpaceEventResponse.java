package com.lumira.backend.card;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record CourseSpaceEventResponse(UUID id, UUID cardId, String type,
                                       UUID actorUserId, Instant createdAt, JsonNode payload) {
    static CourseSpaceEventResponse from(CourseSpaceEvent event) {
        return new CourseSpaceEventResponse(event.getId(), event.getCardId(), event.getType(),
                event.getActorUserId(), event.getCreatedAt(), event.getPayload());
    }
}
