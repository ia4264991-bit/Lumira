package com.lumira.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standard error response envelope per API_CONTRACT.md:
 * {@code { "error": { "code": "string", "message": "string" } }}.
 */
public record ApiErrorResponse(ErrorDetails error) {

    public ApiErrorResponse(String code, String message) {
        this(new ErrorDetails(code, message, null));
    }

    public ApiErrorResponse(String code, String message, List<FieldErrorDetail> details) {
        this(new ErrorDetails(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetails(
            String code,
            String message,
            List<FieldErrorDetail> details
    ) {}

    public record FieldErrorDetail(
            String field,
            String message
    ) {}
}
