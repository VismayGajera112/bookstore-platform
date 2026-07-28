package com.example.common.exception;

/** A request that is well-formed and authorized but conflicts with the current state (HTTP 409). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
