package com.lumira.backend.flashcard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FlashcardSetCreateRequest(@NotBlank String title, @NotNull String description,
        @NotNull List<@Valid FlashcardInput> cards) { }
