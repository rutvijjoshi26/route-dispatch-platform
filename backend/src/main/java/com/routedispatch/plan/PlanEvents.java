package com.routedispatch.plan;

import java.time.LocalTime;

/** Messages pushed to dispatchers while a plan changes. */
public final class PlanEvents {

    /** The solver found a better solution; sent at most once per second. */
    public record SolveProgress(long planId, String reason, long elapsedMillis, String score) {
    }

    /** A solve or re-plan was saved; clients reload the plan. */
    public record PlanUpdated(long planId, int version, String reason, String detail, long totalMillis,
                              long solveMillis, int changedVehicles, int pinnedStops, String score) {
    }

    public record PlanFailed(long planId, String reason, String message) {
    }

    /** A driver event changed a stop. */
    public record StopUpdated(long planId, long stopId, String orderRef, long vehicleId, StopStatus status,
                              LocalTime at) {
    }

    private PlanEvents() {
    }
}
