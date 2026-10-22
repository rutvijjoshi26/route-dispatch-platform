package com.routedispatch.plan.api;

import com.routedispatch.plan.PackageSize;
import com.routedispatch.plan.PlanStatus;
import com.routedispatch.plan.StopStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read models returned by the plan API. Coordinates are [lat, lon] pairs. */
public final class PlanViews {

    public record PlanSummaryView(long id, String name, LocalDate serviceDate, PlanStatus status, int version,
                                  long stops, String depotName, Instant createdAt) {
    }

    public record DepotView(long id, String name, String address, double lat, double lon) {
    }

    public record ClockView(LocalTime now, double timeScale, Instant dispatchedAt) {
    }

    public record TotalsView(int stops, int assigned, int unassigned, int delivered, int failed, int predictedLate,
                             long distanceMeters, long travelSeconds) {
    }

    public record StopView(long id, String orderRef, String customerName, String address, double lat, double lon,
                           LocalTime windowStart, LocalTime windowEnd, PackageSize size, int demand, StopStatus status,
                           Long vehicleId, Integer sequence, LocalTime plannedArrival, LocalTime plannedDeparture,
                           LocalTime committedEta, LocalTime actualArrival, LocalTime actualDeparture, boolean late,
                           boolean addedMidShift) {
    }

    public record RouteView(long vehicleId, String vehicleName, int capacity, LocalTime shiftStart, LocalTime shiftEnd,
                            int load, long distanceMeters, long travelSeconds, LocalTime departDepot,
                            LocalTime returnDepot, List<double[]> path, List<StopView> stops) {
    }

    public record PlanView(long id, String name, LocalDate serviceDate, PlanStatus status, int version, String score,
                           boolean optimizing, DepotView depot, ClockView clock, TotalsView totals,
                           List<RouteView> routes, List<StopView> unassigned) {
    }

    public record RevisionView(int version, String reason, String detail, Instant createdAt, long solveMillis,
                               long totalMillis, String score, long travelSeconds, long distanceMeters, int pinnedStops,
                               int changedVehicles) {
    }

    private PlanViews() {
    }
}
