package com.lumira.backend.flashcard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlashcardSetResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
        String description, List<FlashcardResponse> cards, Instant createdAt, Instant updatedAt) { }
