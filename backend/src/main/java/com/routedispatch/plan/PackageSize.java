package com.routedispatch.plan;

/** Package size of an order: how much van space it takes and how long the hand-off usually lasts. */
public enum PackageSize {
    SMALL(1, 180),
    MEDIUM(2, 240),
    LARGE(4, 360);

    private final int units;
    private final int serviceSeconds;

    PackageSize(int units, int serviceSeconds) {
        this.units = units;
        this.serviceSeconds = serviceSeconds;
    }

    public int units() {
        return units;
    }

    public int serviceSeconds() {
        return serviceSeconds;
    }
}
