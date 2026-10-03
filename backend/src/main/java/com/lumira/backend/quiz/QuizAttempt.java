package com.lumira.backend.quiz;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempt")
public class QuizAttempt extends BaseEntity {
    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(name = "correct_count", nullable = false, updatable = false)
    private int correctCount;
    @Column(name = "total_questions", nullable = false, updatable = false)
    private int totalQuestions;
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected QuizAttempt() { }
    public QuizAttempt(UUID quizId, UUID userId, int correctCount, int totalQuestions, Instant submittedAt) {
        this.quizId = quizId;
        this.userId = userId;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.submittedAt = submittedAt;
    }
    public UUID getQuizId() { return quizId; }
    public UUID getUserId() { return userId; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public Instant getSubmittedAt() { return submittedAt; }
}
