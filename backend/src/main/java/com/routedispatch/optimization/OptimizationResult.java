package com.routedispatch.optimization;

import java.util.List;

/** Best solution found, how long the solve took, and every best-score improvement along the way. */
public record OptimizationResult(RoutingSolution solution, long elapsedMillis, List<ScoreUpdate> timeline) {

    public OptimizationResult {
        timeline = List.copyOf(timeline);
    }
}
