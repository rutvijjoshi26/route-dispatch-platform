package com.routedispatch.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Straight-line estimate: great-circle distance times a circuity factor, driven at a constant average speed.
 * Used in tests and when no OSRM server is available.
 */
public class HaversineTravelTimeProvider implements TravelTimeProvider {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final double circuityFactor;
    private final double metersPerSecond;

    public HaversineTravelTimeProvider(double circuityFactor, double averageSpeedKph) {
        if (circuityFactor < 1.0 || averageSpeedKph <= 0) {
            throw new IllegalArgumentException("circuity factor must be >= 1 and speed positive");
        }
        this.circuityFactor = circuityFactor;
        this.metersPerSecond = averageSpeedKph / 3.6;
    }

    public static double greatCircleMeters(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    @Override
    public TravelMatrix matrix(List<GeoPoint> origins, List<GeoPoint> destinations) {
        long[][] seconds = new long[origins.size()][destinations.size()];
        long[][] meters = new long[origins.size()][destinations.size()];
        for (int i = 0; i < origins.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                long roadMeters = roadMeters(origins.get(i), destinations.get(j));
                meters[i][j] = roadMeters;
                seconds[i][j] = Math.round(roadMeters / metersPerSecond);
            }
        }
        return new TravelMatrix(seconds, meters);
    }

    @Override
    public RouteGeometry route(List<GeoPoint> waypoints) {
        List<Leg> legs = new ArrayList<>();
        for (int i = 1; i < waypoints.size(); i++) {
            GeoPoint from = waypoints.get(i - 1);
            GeoPoint to = waypoints.get(i);
            long roadMeters = roadMeters(from, to);
            legs.add(new Leg(List.of(from, to), roadMeters, Math.round(roadMeters / metersPerSecond)));
        }
        return new RouteGeometry(legs);
    }

    @Override
    public String name() {
        return "haversine";
    }

    private long roadMeters(GeoPoint from, GeoPoint to) {
        return Math.round(greatCircleMeters(from, to) * circuityFactor);
    }
}
