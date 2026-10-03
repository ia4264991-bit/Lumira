package com.lumira.backend.sarah;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Caller-supplied transcript data; roles are deliberately limited to user and assistant. */
public record ConversationTurn(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank String content) { }
