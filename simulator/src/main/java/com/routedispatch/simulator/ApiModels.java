package com.routedispatch.simulator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** The parts of the backend's JSON the simulator reads. Unknown fields are ignored. */
final class ApiModels {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Clock(LocalTime now, LocalTime origin, double timeScale, Instant dispatchedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteStop(long id, String orderRef, int sequence, String status, LocalTime windowStart, LocalTime windowEnd,
                     int serviceSeconds, LocalTime plannedArrival, LocalTime plannedDeparture, List<double[]> inboundLeg) {

        boolean isFinished() {
            return "DELIVERED".equals(status) || "FAILED".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DriverRoute(long planId, int planVersion, long vehicleId, String vehicleName, double depotLat, double depotLon,
                       Clock clock, LocalTime departDepot, List<RouteStop> stops, List<double[]> returnLeg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlanSummary(long id, String name, String status, int version, long stops) {
    }

    private ApiModels() {
    }
}
