package com.eventpulse.exception;

import org.springframework.http.HttpStatus;

public class DuplicateRegistrationException extends ApiException {

    public DuplicateRegistrationException() {
        super(HttpStatus.CONFLICT, "This attendee is already registered for the event");
    }
}
