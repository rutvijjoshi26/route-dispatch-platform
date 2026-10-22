package com.routedispatch.security;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256-signed access tokens. Verification is done by Spring's resource-server JWT decoder. */
@Service
public class TokenService {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    static final String ISSUER = "route-dispatch";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name());
        if (user.getVehicleId() != null) {
            claims.claim("vehicleId", user.getVehicleId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue(), expiresAt);
    }
}
