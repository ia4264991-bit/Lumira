package com.lumira.backend.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizResponse(UUID id, UUID ownerCardId, UUID ownerUserId, String title,
        String description, List<QuizQuestionResponse> questions, Instant createdAt, Instant updatedAt) { }
