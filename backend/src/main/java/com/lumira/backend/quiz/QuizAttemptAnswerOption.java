package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "quiz_attempt_answer_option", uniqueConstraints = @UniqueConstraint(name = "quiz_attempt_answer_option_position_unique", columnNames = {"answer_id", "position"}))
public class QuizAttemptAnswerOption extends BaseEntity {
    @Column(name = "answer_id", nullable = false, updatable = false)
    private UUID answerId;
    @Column(name = "option_id", nullable = false, updatable = false)
    private UUID optionId;
    @Column(name = "position", nullable = false, updatable = false)
    private int position;
    @Column(name = "option_text", nullable = false, columnDefinition = "text", updatable = false)
    private String text;
    @Column(name = "is_selected", nullable = false, updatable = false)
    private boolean selected;
    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    protected QuizAttemptAnswerOption() { }
    public QuizAttemptAnswerOption(UUID answerId, UUID optionId, int position,
            String text, boolean selected, boolean correct) {
        this.answerId = answerId;
        this.optionId = optionId;
        this.position = position;
        this.text = text;
        this.selected = selected;
        this.correct = correct;
    }
    public UUID getAnswerId() { return answerId; }
    public UUID getOptionId() { return optionId; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public boolean isSelected() { return selected; }
    public boolean isCorrect() { return correct; }
}
