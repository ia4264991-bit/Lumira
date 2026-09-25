package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated caller lacks authorization for an action.
 */
public class ForbiddenException extends LumiraException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}
