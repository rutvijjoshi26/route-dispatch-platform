package com.routedispatch.optimization;

import com.routedispatch.routing.GeoPoint;
import java.util.List;

/**
 * Input describing one van. {@code visitIds} is the current route (warm start), of which the first
 * {@code pinnedCount} entries must not move. {@code availableFrom} is "now" when re-planning mid-shift.
 */
public record VehicleSpec(String id, String name, int capacity, GeoPoint depot, long shiftStart, long shiftEnd,
                          long availableFrom, List<String> visitIds, int pinnedCount) {

    public VehicleSpec {
        visitIds = List.copyOf(visitIds);
        if (pinnedCount < 0 || pinnedCount > visitIds.size()) {
            throw new IllegalArgumentException("pinnedCount must be within the route length");
        }
    }

    public static VehicleSpec of(String id, String name, int capacity, GeoPoint depot, long shiftStart, long shiftEnd) {
        return new VehicleSpec(id, name, capacity, depot, shiftStart, shiftEnd, shiftStart, List.of(), 0);
    }

    public VehicleSpec withState(long availableFrom, List<String> visitIds, int pinnedCount) {
        return new VehicleSpec(id, name, capacity, depot, shiftStart, shiftEnd, availableFrom, visitIds, pinnedCount);
    }
}
