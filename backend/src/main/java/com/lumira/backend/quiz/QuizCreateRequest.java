package com.lumira.backend.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuizCreateRequest(@NotBlank String title, @NotNull String description,
        @NotEmpty List<@Valid QuizQuestionInput> questions) { }
