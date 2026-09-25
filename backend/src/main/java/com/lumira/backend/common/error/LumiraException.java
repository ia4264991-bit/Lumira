package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all business and domain errors in Lumira backend.
 */
public class LumiraException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public LumiraException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public LumiraException(ErrorCode errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
