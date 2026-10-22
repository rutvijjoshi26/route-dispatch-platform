package com.routedispatch.optimization;

import com.routedispatch.routing.GeoPoint;

/**
 * Input describing one stop. {@code fixedArrival}/{@code fixedDeparture} are set for stops the driver has
 * already reached or is driving to; {@code committedEta} is the ETA customers were told at dispatch.
 */
public record VisitSpec(String id, GeoPoint point, int demand, long windowStart, long windowEnd, long serviceSeconds,
                        Long fixedArrival, Long fixedDeparture, Long committedEta) {

    public static VisitSpec of(String id, GeoPoint point, int demand, long windowStart, long windowEnd, long serviceSeconds) {
        return new VisitSpec(id, point, demand, windowStart, windowEnd, serviceSeconds, null, null, null);
    }

    public VisitSpec withFixedTimes(long arrival, long departure) {
        return new VisitSpec(id, point, demand, windowStart, windowEnd, serviceSeconds, arrival, departure, committedEta);
    }

    public VisitSpec withCommittedEta(Long eta) {
        return new VisitSpec(id, point, demand, windowStart, windowEnd, serviceSeconds, fixedArrival, fixedDeparture, eta);
    }
}
