package com.routedispatch.routing;

/** The routing engine could not answer (unreachable service, bad response, no route). */
public class RoutingException extends RuntimeException {

    public RoutingException(String message) {
        super(message);
    }

    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
