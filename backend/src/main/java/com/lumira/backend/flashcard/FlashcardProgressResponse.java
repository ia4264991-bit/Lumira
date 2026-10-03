package com.lumira.backend.flashcard;

import java.time.Instant;
import java.util.UUID;

public record FlashcardProgressResponse(UUID flashcardId, ReviewOutcome outcome, Instant reviewedAt) {
    public static FlashcardProgressResponse from(FlashcardProgress progress) {
        return new FlashcardProgressResponse(progress.getFlashcardId(), progress.getOutcome(), progress.getReviewedAt());
    }
}
