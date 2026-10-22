package com.routedispatch.common;

/** The request is valid but not allowed in the resource's current state (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
