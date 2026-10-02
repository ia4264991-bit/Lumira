package com.lumira.backend.common.error;

/**
 * Standard error codes used across the Lumira API surface.
 */
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST"),
    UNAUTHORIZED("UNAUTHORIZED"),
    FORBIDDEN("FORBIDDEN"),
    NOT_FOUND("NOT_FOUND"),
    CONFLICT("CONFLICT"),
    GONE("GONE"),
    VALIDATION_FAILED("VALIDATION_FAILED"),
    GENERATION_VALIDATION_FAILED("GENERATION_VALIDATION_FAILED"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
