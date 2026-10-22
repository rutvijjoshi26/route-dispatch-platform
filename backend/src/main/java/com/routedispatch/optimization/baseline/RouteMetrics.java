package com.routedispatch.optimization.baseline;

import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.Visit;

/**
 * Plain-language quality numbers for an evaluated solution (arrival times must be computed).
 * A stop is on time when the van arrives before its window closes; unassigned stops are not on time.
 */
public record RouteMetrics(int stops, int onTimeStops, int lateStops, int unassignedStops, long totalMeters,
                           long totalTravelSeconds, long maxLateSeconds, int capacityOverflowUnits,
                           long shiftOverrunSeconds, int vehiclesUsed) {

    public static RouteMetrics of(RoutingSolution solution) {
        int onTime = 0;
        int late = 0;
        int unassigned = 0;
        long maxLate = 0;
        for (Visit visit : solution.getVisits()) {
            if (visit.getVehicle() == null) {
                unassigned++;
            } else if (visit.secondsLate() > 0) {
                late++;
                maxLate = Math.max(maxLate, visit.secondsLate());
            } else {
                onTime++;
            }
        }
        long meters = 0;
        long seconds = 0;
        int overflow = 0;
        long overrun = 0;
        int used = 0;
        for (VehicleRoute vehicle : solution.getVehicles()) {
            if (vehicle.getVisits().isEmpty()) {
                continue;
            }
            used++;
            meters += vehicle.totalMeters();
            seconds += vehicle.totalTravelSeconds();
            overflow += Math.max(0, vehicle.totalDemand() - vehicle.getCapacity());
            overrun += Math.max(0, vehicle.returnTime() - vehicle.getShiftEnd());
        }
        return new RouteMetrics(solution.getVisits().size(), onTime, late, unassigned, meters, seconds, maxLate,
                overflow, overrun, used);
    }

    public double onTimeRate() {
        return stops == 0 ? 1.0 : (double) onTimeStops / stops;
    }
}
