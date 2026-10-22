package com.routedispatch.routing;

import java.util.List;

/** Port for road travel times and geometry. Implementations: OSRM (real roads) and haversine (estimate). */
public interface TravelTimeProvider {

    TravelMatrix matrix(List<GeoPoint> origins, List<GeoPoint> destinations);

    RouteGeometry route(List<GeoPoint> waypoints);

    String name();
}
