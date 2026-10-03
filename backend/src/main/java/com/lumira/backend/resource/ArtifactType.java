package com.lumira.backend.resource;

public enum ArtifactType {
    RESOURCE("resource"), NOTE("note"), STUDYSET("studyset");

    private final String apiValue;

    ArtifactType(String apiValue) { this.apiValue = apiValue; }

    public String apiValue() { return apiValue; }

    public static ArtifactType fromApiValue(String value) {
        for (ArtifactType type : values()) if (type.apiValue.equals(value)) return type;
        throw new com.lumira.backend.common.error.ResourceNotFoundException("Artifact not found");
    }
}
