package com.lumira.backend.study;

import java.time.Instant;
import java.util.UUID;

public record StudySetResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
        String description, Instant createdAt, Instant updatedAt, boolean sharedWithThisCourseSpace) {
    public static StudySetResponse from(StudySet set) {
        return from(set, false);
    }
    public static StudySetResponse from(StudySet set, boolean sharedWithThisCourseSpace) {
        return new StudySetResponse(set.getId(), set.getOwner().getOwningCardId(), set.getOwner().getOwningUserId(),
                set.getTitle(), set.getDescription(), set.getCreatedAt(), set.getUpdatedAt(), sharedWithThisCourseSpace);
    }
}
