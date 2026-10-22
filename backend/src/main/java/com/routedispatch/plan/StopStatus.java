package com.routedispatch.plan;

/** Delivery progress of a stop. Status only ever moves forward; DELIVERED and FAILED are final. */
public enum StopStatus {
    PENDING(0),
    EN_ROUTE(1),
    ARRIVED(2),
    DELIVERED(3),
    FAILED(3);

    private final int rank;

    StopStatus(int rank) {
        this.rank = rank;
    }

    int rank() {
        return rank;
    }

    public boolean isTerminal() {
        return rank == 3;
    }
}
