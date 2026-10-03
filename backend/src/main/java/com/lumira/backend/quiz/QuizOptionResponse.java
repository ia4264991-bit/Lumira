package com.lumira.backend.quiz;

import java.util.UUID;

public record QuizOptionResponse(UUID id, int position, String text, Boolean correct) {
    static QuizOptionResponse from(QuizQuestionOption option, boolean revealCorrectness) {
        return new QuizOptionResponse(option.getId(), option.getPosition(), option.getText(),
                revealCorrectness ? option.isCorrect() : null);
    }
}
