package com.lumira.backend.sarah;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record WorkspaceAskRequest(
        @NotNull UUID conversationId,
        @NotBlank String question,
        List<@NotNull @Valid ConversationTurn> conversationHistory) { }
