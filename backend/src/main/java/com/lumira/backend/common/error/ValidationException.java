package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Thrown when input or generated content fails domain or schema validation.
 */
public class ValidationException extends LumiraException {

    private final List<ApiErrorResponse.FieldErrorDetail> fieldErrors;

    public ValidationException(String message) {
        this(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }

    public ValidationException(String message, List<ApiErrorResponse.FieldErrorDetail> fieldErrors) {
        this(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message, fieldErrors);
    }

    public ValidationException(ErrorCode errorCode, HttpStatus status, String message, List<ApiErrorResponse.FieldErrorDetail> fieldErrors) {
        super(errorCode, status, message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : List.of();
    }

    public List<ApiErrorResponse.FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
