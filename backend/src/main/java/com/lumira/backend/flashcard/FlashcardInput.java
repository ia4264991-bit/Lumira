package com.lumira.backend.flashcard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FlashcardInput(UUID id, @NotNull @Min(1) Integer position,
        @NotNull String front, @NotNull String back) { }
