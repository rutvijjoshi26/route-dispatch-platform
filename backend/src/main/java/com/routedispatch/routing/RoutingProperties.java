package com.routedispatch.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dispatch.routing")
public record RoutingProperties(
        @NotNull Provider provider,
        @Valid @NotNull Osrm osrm,
        @Valid @NotNull Haversine haversine,
        @Min(1000) long cacheMaxPairs) {

    public enum Provider { OSRM, HAVERSINE }

    public record Osrm(@NotNull String baseUrl, @Min(1) int tableChunkSize, @NotNull Duration timeout) {
    }

    public record Haversine(double circuityFactor, double averageSpeedKph) {
    }
}
