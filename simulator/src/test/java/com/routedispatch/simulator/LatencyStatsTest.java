package com.routedispatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LatencyStatsTest {

    @Test
    void percentilesUseNearestRank() {
        LatencyStats stats = new LatencyStats();
        for (int i = 1; i <= 100; i++) {
            stats.record(i * 1_000_000L);
        }

        LatencyStats.Summary summary = stats.summary();

        assertThat(summary.count()).isEqualTo(100);
        assertThat(summary.p50Millis()).isEqualTo(50.0);
        assertThat(summary.p95Millis()).isEqualTo(95.0);
        assertThat(summary.p99Millis()).isEqualTo(99.0);
        assertThat(summary.maxMillis()).isEqualTo(100.0);
    }

    @Test
    void emptyStatsAreZero() {
        assertThat(new LatencyStats().summary().count()).isZero();
    }
}
