package com.routedispatch.plan;

import com.routedispatch.fleet.Vehicle;
import com.routedispatch.routing.GeoPoint;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the planner needs to know about a plan at one moment. {@code now} is the plan-clock time for a
 * dispatched plan and null before dispatch. {@code departDepot} holds each van's planned depot departure.
 */
public record PlanState(GeoPoint depot, List<Vehicle> vehicles, List<Stop> stops, Map<Long, LocalTime> departDepot,
                        LocalTime now) {

    public PlanState {
        vehicles = List.copyOf(vehicles);
        stops = List.copyOf(stops);
        departDepot = Map.copyOf(departDepot);
    }

    public boolean isLive() {
        return now != null;
    }

    /** Stop ids per vehicle in their stored sequence. */
    public static Map<Long, List<Long>> currentOrder(List<Stop> stops) {
        Map<Long, List<Long>> order = new LinkedHashMap<>();
        stops.stream()
                .filter(s -> s.getVehicleId() != null)
                .sorted(Comparator.comparing(Stop::getVehicleId).thenComparing(Stop::getSequence))
                .forEach(s -> order.computeIfAbsent(s.getVehicleId(), id -> new ArrayList<>()).add(s.getId()));
        return order;
    }
}
