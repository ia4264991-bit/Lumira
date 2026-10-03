package com.lumira.backend.flashcard;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FlashcardProgressRequest(@NotNull UUID cardId, @NotNull ReviewOutcome outcome) { }
