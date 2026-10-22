package com.routedispatch.security;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dispatch.security")
public record SecurityProperties(
        @NotNull @Size(min = 32, message = "must be at least 32 characters for HS256") String jwtSecret,
        @NotNull Duration tokenTtl,
        @NotEmpty List<String> allowedOrigins) {
}
