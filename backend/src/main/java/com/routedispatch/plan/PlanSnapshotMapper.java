package com.routedispatch.plan;

import com.routedispatch.fleet.Vehicle;
import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.VisitSpec;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds solver input from the stored plan. On a dispatched plan, each van's stops that are no longer PENDING
 * (delivered, failed, arrived, or being driven to) become a pinned prefix with fixed times, and the van cannot
 * leave its current position before "now". Pending stops stay movable; their current order is the warm start.
 */
@Component
public class PlanSnapshotMapper {

    public PlanSnapshot map(PlanState state, Map<Long, List<Long>> order) {
        Map<Long, Stop> stopsById = state.stops().stream().collect(Collectors.toMap(Stop::getId, Function.identity()));
        Set<Long> placed = new HashSet<>();
        List<VisitSpec> visits = new ArrayList<>();
        List<VehicleSpec> vehicles = new ArrayList<>();
        int pinnedTotal = 0;

        for (Vehicle vehicle : state.vehicles()) {
            List<Stop> route = order.getOrDefault(vehicle.getId(), List.of()).stream()
                    .map(stopsById::get).filter(java.util.Objects::nonNull).toList();
            List<Stop> visited = route.stream().filter(s -> !s.isPending()).toList();
            List<Stop> pending = route.stream().filter(Stop::isPending).toList();
            long shiftStart = vehicle.getShiftStart().toSecondOfDay();
            long availableFrom = state.isLive() ? Math.max(shiftStart, state.now().toSecondOfDay()) : shiftStart;

            List<String> ids = new ArrayList<>();
            LocalTime previousPlannedDeparture = state.departDepot().getOrDefault(vehicle.getId(), vehicle.getShiftStart());
            for (Stop stop : visited) {
                visits.add(fixed(spec(stop, state), stop, previousPlannedDeparture, state.now()));
                previousPlannedDeparture = stop.getPlannedDeparture();
                ids.add(stop.getId().toString());
                placed.add(stop.getId());
            }
            for (Stop stop : pending) {
                visits.add(spec(stop, state));
                ids.add(stop.getId().toString());
                placed.add(stop.getId());
            }
            pinnedTotal += visited.size();
            vehicles.add(new VehicleSpec(vehicle.getId().toString(), vehicle.getName(), vehicle.getCapacity(),
                    state.depot(), shiftStart, vehicle.getShiftEnd().toSecondOfDay(), availableFrom, ids, visited.size()));
        }
        for (Stop stop : state.stops()) {
            if (!placed.contains(stop.getId()) && stop.isPending()) {
                visits.add(spec(stop, state));
            }
        }
        return new PlanSnapshot(vehicles, visits, pinnedTotal);
    }

    private static VisitSpec spec(Stop stop, PlanState state) {
        Long eta = state.isLive() && stop.getCommittedEta() != null ? (long) stop.getCommittedEta().toSecondOfDay() : null;
        return new VisitSpec(stop.getId().toString(), stop.getLocation(), stop.getDemand(),
                stop.getWindowStart().toSecondOfDay(), stop.getWindowEnd().toSecondOfDay(), stop.getServiceSeconds(),
                null, null, eta);
    }

    /** Fixed times for a stop the driver has reached or is driving to. */
    private static VisitSpec fixed(VisitSpec spec, Stop stop, LocalTime previousPlannedDeparture, LocalTime now) {
        long nowSeconds = now == null ? 0 : now.toSecondOfDay();
        long arrival;
        long departure;
        switch (stop.getStatus()) {
            case DELIVERED, FAILED -> {
                arrival = stop.getActualArrival().toSecondOfDay();
                departure = stop.getActualDeparture().toSecondOfDay();
            }
            case ARRIVED -> {
                arrival = stop.getActualArrival().toSecondOfDay();
                departure = Math.max(Math.max(arrival, spec.windowStart()) + spec.serviceSeconds(), nowSeconds);
            }
            case EN_ROUTE -> {
                long plannedLeg = stop.getPlannedArrival() == null || previousPlannedDeparture == null ? 0
                        : Math.max(0, stop.getPlannedArrival().toSecondOfDay() - previousPlannedDeparture.toSecondOfDay());
                arrival = Math.max(stop.getEnRouteAt().toSecondOfDay() + plannedLeg, nowSeconds);
                departure = Math.max(arrival, spec.windowStart()) + spec.serviceSeconds();
            }
            default -> throw new IllegalStateException("Stop " + stop.getId() + " is pending");
        }
        return spec.withFixedTimes(arrival, departure);
    }
}
