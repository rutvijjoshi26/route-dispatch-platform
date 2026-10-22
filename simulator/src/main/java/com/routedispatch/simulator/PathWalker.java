package com.routedispatch.simulator;

import java.util.List;

/** Positions along a polyline of [lat, lon] points by distance travelled. */
final class PathWalker {

    record Position(double lat, double lon, double headingDeg) {
    }

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final List<double[]> points;
    private final double[] cumulative;

    PathWalker(List<double[]> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("path needs at least one point");
        }
        this.points = points;
        this.cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + meters(points.get(i - 1), points.get(i));
        }
    }

    double lengthMeters() {
        return cumulative[cumulative.length - 1];
    }

    Position at(double distanceMeters) {
        if (points.size() == 1 || distanceMeters <= 0) {
            double[] first = points.getFirst();
            return new Position(first[0], first[1], points.size() > 1 ? heading(first, points.get(1)) : 0);
        }
        if (distanceMeters >= lengthMeters()) {
            double[] last = points.getLast();
            return new Position(last[0], last[1], heading(points.get(points.size() - 2), last));
        }
        int segment = 1;
        while (cumulative[segment] < distanceMeters) {
            segment++;
        }
        double[] a = points.get(segment - 1);
        double[] b = points.get(segment);
        double span = cumulative[segment] - cumulative[segment - 1];
        double t = span == 0 ? 0 : (distanceMeters - cumulative[segment - 1]) / span;
        return new Position(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, heading(a, b));
    }

    static double meters(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static double heading(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
