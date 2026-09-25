package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation conflicts with current state (e.g. Owner cannot leave without transfer/dissolution).
 */
public class ConflictException extends LumiraException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
