package com.eventpulse.exception;

import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing failures. The handler maps {@link #getStatus()} to the response. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
