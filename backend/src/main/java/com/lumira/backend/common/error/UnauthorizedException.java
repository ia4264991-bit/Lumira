package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an unauthenticated request attempts to access an authenticated endpoint.
 */
public class UnauthorizedException extends LumiraException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
}
