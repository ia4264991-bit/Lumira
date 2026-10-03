package com.lumira.backend.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StudySetCreateRequest(@NotBlank String title, @NotNull String description) { }
