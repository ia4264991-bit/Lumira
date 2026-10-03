package com.lumira.backend.sarah;

import java.util.UUID;

public record SarahAskResponse(String answer, UUID conversationId, int usageUsed, int usageLimit) { }
