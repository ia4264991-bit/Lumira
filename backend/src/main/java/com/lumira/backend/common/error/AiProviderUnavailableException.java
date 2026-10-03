package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

public class AiProviderUnavailableException extends LumiraException {
    public AiProviderUnavailableException(String message) {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    public AiProviderUnavailableException(String message, Throwable cause) {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
