package com.routedispatch.optimization;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

/** A new best score found {@code elapsedMillis} after the solve started. */
public record ScoreUpdate(long elapsedMillis, HardMediumSoftScore score) {
}
