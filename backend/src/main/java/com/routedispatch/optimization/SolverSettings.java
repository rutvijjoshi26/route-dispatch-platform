package com.routedispatch.optimization;

import java.time.Duration;

/** Termination for one kind of solve: stop at {@code spentLimit}, or earlier when no improvement for a while. */
public record SolverSettings(Duration spentLimit, Duration unimprovedSpentLimit) {
}
