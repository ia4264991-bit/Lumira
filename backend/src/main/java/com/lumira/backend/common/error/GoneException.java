package com.lumira.backend.common.error;

import org.springframework.http.HttpStatus;

public class GoneException extends LumiraException {
    public GoneException(String message) {
        super(ErrorCode.GONE, HttpStatus.GONE, message);
    }
}
