package com.routedispatch.tracking;

/** Port for pushing van positions to dispatchers. */
public interface PositionPublisher {

    void positionUpdated(long planId, TrackingDtos.VehiclePositionView position);
}
