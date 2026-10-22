package com.routedispatch.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@IntegrationTest
class AuthIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;
    @Autowired JwtDecoder decoder;

    @Test
    void loginIssuesATokenWithRoleAndVehicleClaims() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(1, 10);

        String token = api.login(fleet.driverEmails().getFirst());

        CurrentUser user = CurrentUser.from(decoder.decode(token));
        assertThat(user.role()).isEqualTo(Role.DRIVER);
        assertThat(user.vehicleId()).isEqualTo(fleet.vans().getFirst().getId());
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(1, 10);

        api.post(null, "/api/auth/login", Map.of("email", fleet.dispatcherEmail(), "password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
        api.post(null, "/api/auth/login", Map.of("email", "nobody@test.local", "password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        var otherKey = new SecretKeySpec("a-completely-different-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("route-dispatch").subject("1").claim("role", "DISPATCHER")
                        .expiresAt(Instant.now().plusSeconds(600)).build())).getTokenValue();

        api.get(forged, "/api/plans").andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        api.get(null, "/actuator/health").andExpect(status().isOk());
        api.get(null, "/v3/api-docs").andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/plans']").exists());
    }
}
