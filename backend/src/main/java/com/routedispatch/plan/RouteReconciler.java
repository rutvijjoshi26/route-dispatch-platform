package com.routedispatch.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Merges a solver result with the current database state. Drivers keep reporting while the solver runs, so a
 * stop the solver treated as movable may already be in progress on some van. What really happened wins: each van
 * keeps its non-pending stops (in stored order) and then takes the solver's order for stops that are still
 * pending. Every stop ends up on exactly one van or in the unassigned list.
 */
@Component
public class RouteReconciler {

    public record Result(Map<Long, List<Long>> orders, List<Long> unassigned) {
    }

    public Result reconcile(Map<Long, List<Long>> solved, List<Stop> current, List<Long> vehicleIds) {
        Map<Long, Stop> byId = new LinkedHashMap<>();
        current.forEach(s -> byId.put(s.getId(), s));
        Set<Long> placed = new HashSet<>();
        Map<Long, List<Long>> orders = new LinkedHashMap<>();
        for (Long vehicleId : vehicleIds) {
            List<Long> route = new ArrayList<>();
            current.stream()
                    .filter(s -> !s.isPending() && vehicleId.equals(s.getVehicleId()))
                    .sorted(Comparator.comparing(Stop::getSequence))
                    .forEach(s -> {
                        route.add(s.getId());
                        placed.add(s.getId());
                    });
            for (Long stopId : solved.getOrDefault(vehicleId, List.of())) {
                Stop stop = byId.get(stopId);
                if (stop != null && stop.isPending() && placed.add(stopId)) {
                    route.add(stopId);
                }
            }
            orders.put(vehicleId, route);
        }
        List<Long> unassigned = current.stream()
                .filter(s -> s.isPending() && !placed.contains(s.getId()))
                .map(Stop::getId)
                .toList();
        return new Result(orders, unassigned);
    }
}
