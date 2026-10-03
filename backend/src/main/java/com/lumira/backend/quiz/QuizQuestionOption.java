package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "quiz_question_option", uniqueConstraints = @UniqueConstraint(name = "quiz_option_position_unique", columnNames = {"question_id", "position"}))
public class QuizQuestionOption extends BaseEntity {
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;
    @Column(name = "position", nullable = false)
    private int position;
    @Column(name = "option_text", nullable = false, columnDefinition = "text")
    private String text;
    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    protected QuizQuestionOption() { }
    public QuizQuestionOption(UUID questionId, int position, String text, boolean correct) {
        this.questionId = questionId;
        this.position = position;
        this.text = text;
        this.correct = correct;
    }
    public UUID getQuestionId() { return questionId; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public boolean isCorrect() { return correct; }
    public void moveTemporarily(int position) { this.position = position; }
    public void update(int position, String text, boolean correct) {
        this.position = position;
        this.text = text;
        this.correct = correct;
    }
}
