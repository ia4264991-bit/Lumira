package com.lumira.backend.sarah;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SarahGenerationRequest(@NotNull SarahGenerationType artifactType,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> sourceResourceIds,
        @Size(max = 4000) String instructions) { }
