package com.eventpulse.exception;

import org.springframework.http.HttpStatus;

/** A request that is well-formed but violates a domain rule (e.g. registering for a draft event). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
