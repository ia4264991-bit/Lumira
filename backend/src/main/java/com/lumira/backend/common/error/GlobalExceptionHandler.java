package com.lumira.backend.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler providing uniform error responses conforming to API_CONTRACT.md:
 * {@code { "error": { "code": "string", "message": "string" } }}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LumiraException.class)
    public ResponseEntity<ApiErrorResponse> handleLumiraException(LumiraException ex) {
        log.warn("Domain exception [{}]: {}", ex.getErrorCode().getValue(), ex.getMessage());
        List<ApiErrorResponse.FieldErrorDetail> details = null;
        if (ex instanceof ValidationException ve && !ve.getFieldErrors().isEmpty()) {
            details = ve.getFieldErrors();
        }
        ApiErrorResponse body;
        if (ex instanceof AccountDeletionConflictException deletionConflict) {
            body = new ApiErrorResponse(new ApiErrorResponse.ErrorDetails(
                    ex.getErrorCode().getValue(), ex.getMessage(),
                    java.util.Map.of("courseSpaceCardIds", deletionConflict.getCourseSpaceCardIds())));
        } else {
            body = new ApiErrorResponse(ex.getErrorCode().getValue(), ex.getMessage(), details);
        }
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ApiErrorResponse.FieldErrorDetail(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            ));
        }
        String message = fieldErrors.isEmpty()
                ? "Validation failed"
                : "Validation failed for " + fieldErrors.size() + " field(s)";

        log.warn("Validation error on request: {}", message);
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.getValue(),
                message,
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "field";
            fieldErrors.add(new ApiErrorResponse.FieldErrorDetail(propertyPath, violation.getMessage()));
        });

        log.warn("Constraint violation: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.VALIDATION_FAILED.getValue(),
                "Validation constraints violated",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable HTTP message: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.BAD_REQUEST.getValue(),
                "Malformed JSON or unreadable request body"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.METHOD_NOT_ALLOWED.getValue(),
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint"
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.getValue(),
                "Media type " + ex.getContentType() + " is not supported"
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Endpoint not found: {}", ex.getResourcePath());
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.NOT_FOUND.getValue(),
                "Endpoint not found: " + ex.getResourcePath()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error", ex);
        ApiErrorResponse body = new ApiErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getValue(),
                "An unexpected internal error occurred"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
