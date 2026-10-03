package com.lumira.backend.sarah;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record ContextualAskRequest(
        @NotNull UUID conversationId,
        @NotNull UUID cardId,
        String selectedText,
        @NotBlank String question,
        List<@NotNull @Valid ConversationTurn> conversationHistory,
        @PositiveOrZero Integer pageIndex) { }
