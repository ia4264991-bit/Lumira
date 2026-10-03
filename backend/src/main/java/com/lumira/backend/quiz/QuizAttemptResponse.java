package com.lumira.backend.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizAttemptResponse(UUID id, UUID quizId, UUID userId, int correctCount,
        int totalQuestions, Instant createdAt, Instant submittedAt, List<AnswerSnapshot> answers) {
    public record AnswerSnapshot(UUID questionId, String prompt, UUID selectedOptionId,
            String selectedOptionText, UUID correctOptionId, String correctOptionText,
            boolean correct, List<OptionSnapshot> options) { }
    public record OptionSnapshot(UUID id, int position, String text, boolean selected, boolean correct) { }
}
