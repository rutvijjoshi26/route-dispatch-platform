package com.routedispatch.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Identity carried in a verified access token. */
public record CurrentUser(long userId, String email, Role role, Long vehicleId) {

    public static CurrentUser from(Jwt jwt) {
        Object vehicle = jwt.getClaim("vehicleId");
        return new CurrentUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("email"),
                Role.valueOf(jwt.getClaimAsString("role")), vehicle == null ? null : ((Number) vehicle).longValue());
    }
}
