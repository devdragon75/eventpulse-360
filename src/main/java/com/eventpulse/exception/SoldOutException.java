package com.eventpulse.exception;

import org.springframework.http.HttpStatus;

public class SoldOutException extends ApiException {

    public SoldOutException(String ticketTypeName) {
        super(HttpStatus.CONFLICT, "Ticket type '" + ticketTypeName + "' is sold out");
    }
}
