package com.lumira.backend.sarah;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SarahGenerationType {
    FLASHCARDSET("flashcardset"), QUIZ("quiz"), STUDYSET("studyset");
    private final String apiValue;
    SarahGenerationType(String apiValue) { this.apiValue = apiValue; }
    @JsonValue public String apiValue() { return apiValue; }
    @JsonCreator public static SarahGenerationType from(String value) {
        for (SarahGenerationType type : values()) if (type.apiValue.equalsIgnoreCase(value)) return type;
        throw new IllegalArgumentException("Unsupported Sarah generation type");
    }
}
