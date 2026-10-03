package com.lumira.backend.card;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(@NotNull UUID targetUserId) {}
