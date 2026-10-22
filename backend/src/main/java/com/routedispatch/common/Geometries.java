package com.routedispatch.common;

import com.routedispatch.routing.GeoPoint;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Converts between {@link GeoPoint} and JTS geometries stored in PostGIS (SRID 4326, x = longitude). */
public final class Geometries {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public static Point point(GeoPoint point) {
        return FACTORY.createPoint(new Coordinate(point.lon(), point.lat()));
    }

    public static GeoPoint geoPoint(Point point) {
        return new GeoPoint(point.getY(), point.getX());
    }

    /** Returns null for fewer than two points, which PostGIS cannot store as a line. */
    public static LineString lineString(List<GeoPoint> path) {
        if (path.size() < 2) {
            return null;
        }
        Coordinate[] coordinates = path.stream().map(p -> new Coordinate(p.lon(), p.lat())).toArray(Coordinate[]::new);
        return FACTORY.createLineString(coordinates);
    }

    public static List<GeoPoint> path(LineString line) {
        if (line == null) {
            return List.of();
        }
        return java.util.Arrays.stream(line.getCoordinates()).map(c -> new GeoPoint(c.getY(), c.getX())).toList();
    }

    private Geometries() {
    }
}
