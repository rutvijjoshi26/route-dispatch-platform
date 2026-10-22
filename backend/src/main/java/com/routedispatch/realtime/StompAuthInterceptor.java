package com.routedispatch.realtime;

import com.routedispatch.security.CurrentUser;
import com.routedispatch.security.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the same bearer token as the REST API (sent in the CONNECT frame's
 * Authorization header) and only lets dispatchers subscribe to plan topics. Clients cannot send to the broker.
 */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;

    StompAuthInterceptor(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        this.decoder = decoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Clients may not publish messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK and heartbeats need no checks.
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            Jwt jwt = decoder.decode(header.substring("Bearer ".length()));
            return (JwtAuthenticationToken) converter.convert(jwt);
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid token");
        }
    }

    private static void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken token)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        String destination = accessor.getDestination();
        CurrentUser user = CurrentUser.from(token.getToken());
        if (destination == null || !destination.startsWith("/topic/plans/") || user.role() != Role.DISPATCHER) {
            throw new MessageDeliveryException("Not allowed to subscribe to " + destination);
        }
    }
}
