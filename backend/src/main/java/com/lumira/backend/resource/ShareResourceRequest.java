package com.lumira.backend.resource;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareResourceRequest(@NotNull UUID cardId) { }
