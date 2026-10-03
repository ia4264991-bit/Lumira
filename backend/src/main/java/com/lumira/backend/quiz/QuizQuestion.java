package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "quiz_question", uniqueConstraints = @UniqueConstraint(name = "quiz_question_position_unique", columnNames = {"quiz_id", "position"}))
public class QuizQuestion extends BaseEntity {
    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;
    @Column(name = "position", nullable = false)
    private int position;
    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    protected QuizQuestion() { }
    public QuizQuestion(UUID quizId, int position, String prompt) {
        this.quizId = quizId;
        this.position = position;
        this.prompt = prompt;
    }
    public UUID getQuizId() { return quizId; }
    public int getPosition() { return position; }
    public String getPrompt() { return prompt; }
    public void moveTemporarily(int position) { this.position = position; }
    public void update(int position, String prompt) { this.position = position; this.prompt = prompt; }
}
