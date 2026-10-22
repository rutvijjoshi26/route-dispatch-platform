package com.routedispatch.tracking;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code threshold}: plan-time delay that counts as running late. {@code vehicleCooldown}: plan time before the
 * same van can trigger another late re-plan. {@code checkInterval}: real time between scans of in-progress stops.
 */
@Validated
@ConfigurationProperties("dispatch.lateness")
public record LatenessProperties(@NotNull Duration threshold, @NotNull Duration vehicleCooldown,
                                 @NotNull Duration checkInterval) {
}
