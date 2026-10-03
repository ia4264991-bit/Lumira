package com.lumira.backend.sarah;

import java.util.List;
import java.util.UUID;

public record SarahGeneratedArtifact(SarahGenerationType artifactType, Object artifact, Provenance provenance,
        int usageUsed, int usageLimit) {
    public record Provenance(String generatedBy, List<Source> sources) { }
    public record Source(String artifactType, UUID artifactId) { }
}
