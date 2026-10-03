package com.lumira.backend.sarah;

import java.util.List;
import java.util.UUID;

public record SarahGenerationPrompt(UUID cardId, String artifactType, String instructions,
        List<SarahContextItem> sources) { }
