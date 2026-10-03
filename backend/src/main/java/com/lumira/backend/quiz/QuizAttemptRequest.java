package com.lumira.backend.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record QuizAttemptRequest(@NotEmpty List<@Valid AnswerInput> answers) {
    public record AnswerInput(UUID questionId, UUID optionId) { }
}
