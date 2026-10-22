package com.routedispatch.plan;

/** What a driver reports about a stop. Each event moves the stop to the matching status. */
public enum StopEventType {
    EN_ROUTE(StopStatus.EN_ROUTE),
    ARRIVED(StopStatus.ARRIVED),
    DELIVERED(StopStatus.DELIVERED),
    FAILED(StopStatus.FAILED);

    private final StopStatus target;

    StopEventType(StopStatus target) {
        this.target = target;
    }

    public StopStatus target() {
        return target;
    }
}
