package com.routedispatch.optimization.baseline;

import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.Visit;
import java.util.ArrayList;
import java.util.List;

/** Naive baseline: deal orders to vans like cards, in the order they were entered, and drive them in that order. */
public class RoundRobinPlanner {

    public RoutingSolution plan(RoutingSolution problem) {
        List<VehicleRoute> vehicles = problem.getVehicles();
        vehicles.forEach(v -> v.setVisits(new ArrayList<>()));
        List<Visit> visits = problem.getVisits();
        for (int i = 0; i < visits.size(); i++) {
            vehicles.get(i % vehicles.size()).getVisits().add(visits.get(i));
        }
        return problem;
    }
}
