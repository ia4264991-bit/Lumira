package com.lumira.backend.card;

import jakarta.validation.constraints.NotNull;

public record RequireApprovalRequest(@NotNull Boolean requireApproval) {}
