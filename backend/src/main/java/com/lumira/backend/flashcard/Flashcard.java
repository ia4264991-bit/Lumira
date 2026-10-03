package com.lumira.backend.flashcard;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "flashcard", uniqueConstraints =
        @UniqueConstraint(name = "flashcard_set_position_unique", columnNames = {"flashcard_set_id", "position"}))
public class Flashcard extends BaseEntity {
    @Column(name = "flashcard_set_id", nullable = false, updatable = false)
    private UUID flashcardSetId;
    @Column(name = "position", nullable = false)
    private int position;
    @Column(name = "front", nullable = false, columnDefinition = "text")
    private String front;
    @Column(name = "back", nullable = false, columnDefinition = "text")
    private String back;

    protected Flashcard() { }
    public Flashcard(UUID flashcardSetId, int position, String front, String back) {
        this.flashcardSetId = flashcardSetId;
        this.position = position;
        this.front = front;
        this.back = back;
    }
    public UUID getFlashcardSetId() { return flashcardSetId; }
    public int getPosition() { return position; }
    public String getFront() { return front; }
    public String getBack() { return back; }
    public void moveTemporarily(int position) { this.position = position; }
    public void update(int position, String front, String back) {
        this.position = position;
        this.front = front;
        this.back = back;
    }
}
