package com.lumira.backend.flashcard;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "flashcard_progress", uniqueConstraints =
        @UniqueConstraint(name = "flashcard_progress_user_card_unique", columnNames = {"user_id", "flashcard_id"}))
public class FlashcardProgress extends BaseEntity {
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(name = "flashcard_id", nullable = false, updatable = false)
    private UUID flashcardId;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private ReviewOutcome outcome;
    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected FlashcardProgress() { }
    public FlashcardProgress(UUID userId, UUID flashcardId, ReviewOutcome outcome, Instant reviewedAt) {
        this.userId = userId;
        this.flashcardId = flashcardId;
        update(outcome, reviewedAt);
    }
    public UUID getUserId() { return userId; }
    public UUID getFlashcardId() { return flashcardId; }
    public ReviewOutcome getOutcome() { return outcome; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void update(ReviewOutcome outcome, Instant reviewedAt) {
        this.outcome = outcome;
        this.reviewedAt = reviewedAt;
    }
}
