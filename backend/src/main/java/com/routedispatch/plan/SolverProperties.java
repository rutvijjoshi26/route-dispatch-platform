package com.routedispatch.plan;

import com.routedispatch.optimization.SolverSettings;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dispatch.solver")
public record SolverProperties(@NotNull SolverSettings initial, @NotNull SolverSettings replan) {
}
