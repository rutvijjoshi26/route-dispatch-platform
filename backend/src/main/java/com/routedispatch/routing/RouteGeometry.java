package com.routedispatch.routing;

import java.util.ArrayList;
import java.util.List;

/** Road geometry of a multi-stop route, one leg per pair of consecutive waypoints. */
public record RouteGeometry(List<Leg> legs) {

    public RouteGeometry {
        legs = List.copyOf(legs);
    }

    /** All legs joined into one polyline without repeating the shared waypoint between legs. */
    public List<GeoPoint> fullPath() {
        List<GeoPoint> path = new ArrayList<>();
        for (Leg leg : legs) {
            for (GeoPoint point : leg.path()) {
                if (path.isEmpty() || !path.getLast().equals(point)) {
                    path.add(point);
                }
            }
        }
        return path;
    }
}
