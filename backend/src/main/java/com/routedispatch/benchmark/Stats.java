package com.routedispatch.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Latency summary using the nearest-rank percentile method. */
record Stats(int samples, long min, long median, long p95, long max, double mean) {

    static Stats of(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        return new Stats(sorted.size(), sorted.getFirst(), rank(sorted, 0.50), rank(sorted, 0.95), sorted.getLast(),
                Math.round(mean * 10) / 10.0);
    }

    private static long rank(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }
}
