package com.routedispatch.common;

/** The caller is authenticated but may not act on this resource (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
