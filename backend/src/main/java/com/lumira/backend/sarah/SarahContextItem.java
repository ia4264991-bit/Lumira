package com.lumira.backend.sarah;

import java.util.UUID;

public record SarahContextItem(String artifactType, UUID artifactId, String title, String content) { }
