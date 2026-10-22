package com.routedispatch.optimization;

import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.TravelMatrix;

/** A point in the planning problem, identified by its row/column in the shared travel matrix. */
public final class Location {

    private final int index;
    private final GeoPoint point;
    private final TravelMatrix matrix;

    public Location(int index, GeoPoint point, TravelMatrix matrix) {
        this.index = index;
        this.point = point;
        this.matrix = matrix;
    }

    public long secondsTo(Location other) {
        return matrix.seconds(index, other.index);
    }

    public long metersTo(Location other) {
        return matrix.meters(index, other.index);
    }

    public int index() {
        return index;
    }

    public GeoPoint point() {
        return point;
    }

    @Override
    public String toString() {
        return "Location[" + index + "]";
    }
}
