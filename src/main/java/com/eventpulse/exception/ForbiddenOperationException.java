package com.eventpulse.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException() {
        super(HttpStatus.FORBIDDEN, "You do not have permission to manage this resource");
    }
}
