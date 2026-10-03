package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "quiz_attempt_answer")
public class QuizAttemptAnswer extends BaseEntity {
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;
    @Column(name = "question_position", nullable = false, updatable = false)
    private int questionPosition;
    @Column(name = "prompt_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    private String promptSnapshot;
    @Column(name = "selected_option_id", nullable = false, updatable = false)
    private UUID selectedOptionId;
    @Column(name = "selected_option_text", nullable = false, columnDefinition = "text", updatable = false)
    private String selectedOptionText;
    @Column(name = "correct_option_id", nullable = false, updatable = false)
    private UUID correctOptionId;
    @Column(name = "correct_option_text", nullable = false, columnDefinition = "text", updatable = false)
    private String correctOptionText;
    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    protected QuizAttemptAnswer() { }
    public QuizAttemptAnswer(UUID attemptId, UUID questionId, int questionPosition, String promptSnapshot,
            UUID selectedOptionId, String selectedOptionText, UUID correctOptionId,
            String correctOptionText, boolean correct) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.questionPosition = questionPosition;
        this.promptSnapshot = promptSnapshot;
        this.selectedOptionId = selectedOptionId;
        this.selectedOptionText = selectedOptionText;
        this.correctOptionId = correctOptionId;
        this.correctOptionText = correctOptionText;
        this.correct = correct;
    }
    public UUID getAttemptId() { return attemptId; }
    public UUID getQuestionId() { return questionId; }
    public int getQuestionPosition() { return questionPosition; }
    public String getPromptSnapshot() { return promptSnapshot; }
    public UUID getSelectedOptionId() { return selectedOptionId; }
    public String getSelectedOptionText() { return selectedOptionText; }
    public UUID getCorrectOptionId() { return correctOptionId; }
    public String getCorrectOptionText() { return correctOptionText; }
    public boolean isCorrect() { return correct; }
}
