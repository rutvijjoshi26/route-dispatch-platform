package com.routedispatch.simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-safe latency recorder with nearest-rank percentiles. */
final class LatencyStats {

    record Summary(int count, double p50Millis, double p95Millis, double p99Millis, double maxMillis, double meanMillis) {
    }

    private final List<Long> nanos = new ArrayList<>();

    synchronized void record(long latencyNanos) {
        nanos.add(latencyNanos);
    }

    synchronized Summary summary() {
        if (nanos.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        return new Summary(sorted.size(), rank(sorted, 0.50), rank(sorted, 0.95), rank(sorted, 0.99),
                sorted.getLast() / 1e6, Math.round(mean * 100) / 100.0);
    }

    private static double rank(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index)) / 1e6;
    }
}
