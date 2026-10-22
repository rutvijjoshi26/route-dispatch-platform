package com.routedispatch.optimization.baseline;

import com.routedispatch.optimization.Location;
import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.Visit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Classic greedy baseline. Vans are filled one after another; each van keeps driving to the closest remaining
 * stop that still fits its capacity, its delivery window, and the driver's shift. Stops no van can take feasibly
 * are appended to the van with the most spare capacity, so every order is still on a route.
 */
public class NearestNeighborPlanner {

    public RoutingSolution plan(RoutingSolution problem) {
        Set<Visit> remaining = new LinkedHashSet<>(problem.getVisits());
        for (VehicleRoute vehicle : problem.getVehicles()) {
            vehicle.setVisits(new ArrayList<>());
            Location here = vehicle.getDepot();
            long clock = vehicle.departureTime();
            int load = 0;
            while (true) {
                Visit next = null;
                long bestTravel = Long.MAX_VALUE;
                for (Visit candidate : remaining) {
                    long travel = here.secondsTo(candidate.getLocation());
                    if (travel < bestTravel && feasible(vehicle, here, clock, load, candidate)) {
                        bestTravel = travel;
                        next = candidate;
                    }
                }
                if (next == null) {
                    break;
                }
                long arrival = clock + bestTravel;
                clock = Math.max(arrival, next.getWindowStart()) + next.getServiceSeconds();
                load += next.getDemand();
                here = next.getLocation();
                vehicle.getVisits().add(next);
                remaining.remove(next);
            }
        }
        for (Visit leftover : remaining) {
            problem.getVehicles().stream()
                    .max(Comparator.comparingInt(v -> v.getCapacity() - v.totalDemand()))
                    .orElseThrow()
                    .getVisits()
                    .add(leftover);
        }
        return problem;
    }

    private static boolean feasible(VehicleRoute vehicle, Location here, long clock, int load, Visit candidate) {
        if (load + candidate.getDemand() > vehicle.getCapacity()) {
            return false;
        }
        long arrival = clock + here.secondsTo(candidate.getLocation());
        if (arrival > candidate.getWindowEnd()) {
            return false;
        }
        long departure = Math.max(arrival, candidate.getWindowStart()) + candidate.getServiceSeconds();
        return departure + candidate.getLocation().secondsTo(vehicle.getDepot()) <= vehicle.getShiftEnd();
    }
}
