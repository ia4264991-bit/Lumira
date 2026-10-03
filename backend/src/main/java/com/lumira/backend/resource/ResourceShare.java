package com.lumira.backend.resource;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "artifact_share")
public class ResourceShare extends BaseEntity {
    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;
    @Column(name = "note_id", updatable = false)
    private UUID noteId;
    @Column(name = "study_set_id", updatable = false)
    private UUID studySetId;
    @Column(name = "quiz_id", updatable = false)
    private UUID quizId;
    @Column(name = "flashcard_set_id", updatable = false)
    private UUID flashcardSetId;
    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;
    @Column(name = "active", nullable = false)
    private boolean active;

    protected ResourceShare() { }
    public ResourceShare(UUID resourceId, UUID cardId) {
        this.resourceId = resourceId;
        this.cardId = cardId;
        this.active = true;
    }
    public ResourceShare(ArtifactType type, UUID artifactId, UUID cardId) {
        switch (type) {
            case RESOURCE -> resourceId = artifactId;
            case NOTE -> noteId = artifactId;
            case STUDYSET -> studySetId = artifactId;
            case QUIZ -> quizId = artifactId;
            case FLASHCARD_SET -> flashcardSetId = artifactId;
        }
        this.cardId = cardId;
        this.active = true;
    }
    public UUID getResourceId() { return resourceId; }
    public UUID getNoteId() { return noteId; }
    public UUID getStudySetId() { return studySetId; }
    public UUID getQuizId() { return quizId; }
    public UUID getFlashcardSetId() { return flashcardSetId; }
    public UUID getCardId() { return cardId; }
    public boolean isActive() { return active; }
    public ArtifactType getArtifactType() {
        if (resourceId != null) return ArtifactType.RESOURCE;
        if (noteId != null) return ArtifactType.NOTE;
        if (studySetId != null) return ArtifactType.STUDYSET;
        if (quizId != null) return ArtifactType.QUIZ;
        return ArtifactType.FLASHCARD_SET;
    }
    public UUID getArtifactId() {
        return switch (getArtifactType()) {
            case RESOURCE -> resourceId;
            case NOTE -> noteId;
            case STUDYSET -> studySetId;
            case QUIZ -> quizId;
            case FLASHCARD_SET -> flashcardSetId;
        };
    }
    public void activate() { active = true; }
    public void deactivate() { active = false; }
}
