package com.lumira.backend.study;

import java.time.Instant;
import java.util.UUID;

public record StudySetResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
        String description, Instant createdAt, Instant updatedAt) {
    public static StudySetResponse from(StudySet set) {
        return new StudySetResponse(set.getId(), set.getOwner().getOwningCardId(), set.getOwner().getOwningUserId(),
                set.getTitle(), set.getDescription(), set.getCreatedAt(), set.getUpdatedAt());
    }
}
