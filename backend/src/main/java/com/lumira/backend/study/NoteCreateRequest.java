package com.lumira.backend.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoteCreateRequest(@NotBlank String title, @NotNull String content) { }
