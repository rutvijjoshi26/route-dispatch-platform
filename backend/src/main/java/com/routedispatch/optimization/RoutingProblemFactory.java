package com.routedispatch.optimization;

import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.TravelMatrix;
import com.routedispatch.routing.TravelMatrixService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns vehicle and visit inputs into a Timefold problem backed by one shared travel matrix. */
public class RoutingProblemFactory {

    private final TravelMatrixService matrixService;

    public RoutingProblemFactory(TravelMatrixService matrixService) {
        this.matrixService = matrixService;
    }

    public RoutingSolution create(List<VehicleSpec> vehicleSpecs, List<VisitSpec> visitSpecs) {
        Map<GeoPoint, Integer> pointIndex = new LinkedHashMap<>();
        vehicleSpecs.forEach(v -> pointIndex.putIfAbsent(v.depot(), pointIndex.size()));
        visitSpecs.forEach(v -> pointIndex.putIfAbsent(v.point(), pointIndex.size()));
        List<GeoPoint> points = new ArrayList<>(pointIndex.keySet());
        TravelMatrix matrix = matrixService.matrix(points);
        List<Location> locations = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            locations.add(new Location(i, points.get(i), matrix));
        }

        Map<String, Visit> visitsById = new HashMap<>();
        List<Visit> visits = new ArrayList<>(visitSpecs.size());
        for (VisitSpec spec : visitSpecs) {
            Visit visit = new Visit(spec.id(), locations.get(pointIndex.get(spec.point())), spec.demand(),
                    spec.windowStart(), spec.windowEnd(), spec.serviceSeconds());
            if (spec.fixedArrival() != null) {
                visit.fix(spec.fixedArrival(), spec.fixedDeparture() == null ? spec.fixedArrival() : spec.fixedDeparture());
            }
            visit.setCommittedEta(spec.committedEta());
            if (visitsById.put(spec.id(), visit) != null) {
                throw new IllegalArgumentException("Duplicate visit id " + spec.id());
            }
            visits.add(visit);
        }

        List<VehicleRoute> vehicles = new ArrayList<>(vehicleSpecs.size());
        for (VehicleSpec spec : vehicleSpecs) {
            VehicleRoute vehicle = new VehicleRoute(spec.id(), spec.name(), spec.capacity(),
                    locations.get(pointIndex.get(spec.depot())), spec.shiftStart(), spec.shiftEnd(), spec.availableFrom());
            List<Visit> route = new ArrayList<>(spec.visitIds().size());
            for (String visitId : spec.visitIds()) {
                Visit visit = visitsById.get(visitId);
                if (visit == null) {
                    throw new IllegalArgumentException("Vehicle " + spec.id() + " references unknown visit " + visitId);
                }
                route.add(visit);
            }
            vehicle.setVisits(route);
            vehicle.setPinnedCount(spec.pinnedCount());
            vehicles.add(vehicle);
        }
        return new RoutingSolution(vehicles, visits);
    }
}
