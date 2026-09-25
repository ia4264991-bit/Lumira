package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found or when access is not permitted
 * without distinguishing non-existence from unauthorized state (AD-056 BOLA/IDOR guard).
 */
public class ResourceNotFoundException extends LumiraException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
