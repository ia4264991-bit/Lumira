package com.lumira.backend.study;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
        String content, Instant createdAt, Instant updatedAt) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(note.getId(), note.getOwner().getOwningCardId(), note.getOwner().getOwningUserId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
