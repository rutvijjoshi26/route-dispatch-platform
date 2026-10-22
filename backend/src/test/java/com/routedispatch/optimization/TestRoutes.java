package com.routedispatch.optimization;

import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.TravelMatrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Hand-built fixtures with an explicit travel matrix so expected times are easy to read. */
final class TestRoutes {

    static final long H = 3600;
    static final long M = 60;

    /**
     * Location 0 = depot, 1 = A, 2 = B, 3 = C. Travel times in seconds:
     * depot-A 600, depot-B 900, depot-C 1200, A-B 300, A-C 700, B-C 400 (symmetric).
     * Distances are travel seconds times 10 meters.
     */
    static List<Location> locations() {
        long[][] s = {
                {0, 600, 900, 1200},
                {600, 0, 300, 700},
                {900, 300, 0, 400},
                {1200, 700, 400, 0}};
        long[][] m = new long[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[i][j] = s[i][j] * 10;
            }
        }
        TravelMatrix matrix = new TravelMatrix(s, m);
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            locations.add(new Location(i, new GeoPoint(41.8 + i * 0.01, -87.7), matrix));
        }
        return locations;
    }

    static Visit visit(String id, Location location, int demand, long windowStart, long windowEnd) {
        return new Visit(id, location, demand, windowStart, windowEnd, 5 * M);
    }

    static VehicleRoute vehicle(String id, Location depot, int capacity, long shiftStart, long shiftEnd) {
        return new VehicleRoute(id, "Van " + id, capacity, depot, shiftStart, shiftEnd, shiftStart);
    }

    /** Assigns visits to the vehicle and computes shadow variables the same way the solver does. */
    static void connect(VehicleRoute vehicle, Visit... visits) {
        vehicle.setVisits(new ArrayList<>(Arrays.asList(visits)));
        Visit previous = null;
        for (Visit visit : visits) {
            visit.setVehicle(vehicle);
            visit.setPreviousVisit(previous);
            if (previous != null) {
                previous.setNextVisit(visit);
            }
            visit.setArrivalTime(visit.arrivalTimeSupplier());
            previous = visit;
        }
    }

    private TestRoutes() {
    }
}
