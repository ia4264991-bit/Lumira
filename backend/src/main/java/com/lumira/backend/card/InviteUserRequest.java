package com.lumira.backend.card;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InviteUserRequest(@NotNull UUID userId) {}
