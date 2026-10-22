package com.routedispatch.routing;

/**
 * Travel durations (seconds) and road distances (meters) from each origin (row) to each destination (column).
 */
public final class TravelMatrix {

    /** Used when the road network has no path between two points (for example a point snapped to an island). */
    public static final long UNREACHABLE_SECONDS = 86_400;
    public static final long UNREACHABLE_METERS = 1_000_000;

    private final long[][] seconds;
    private final long[][] meters;

    public TravelMatrix(long[][] seconds, long[][] meters) {
        if (seconds.length != meters.length) {
            throw new IllegalArgumentException("Duration and distance matrices differ in size");
        }
        this.seconds = seconds;
        this.meters = meters;
    }

    public long seconds(int from, int to) {
        return seconds[from][to];
    }

    public long meters(int from, int to) {
        return meters[from][to];
    }

    /** Number of rows. For square matrices this is the number of locations. */
    public int size() {
        return seconds.length;
    }
}
