package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

public class RateLimitException extends LumiraException {
    public RateLimitException(String message) {
        super(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
