package com.routedispatch.routing;

import java.util.List;

/** Road path between two consecutive waypoints. */
public record Leg(List<GeoPoint> path, long meters, long seconds) {

    public Leg {
        path = List.copyOf(path);
    }
}
