package com.routedispatch.simulator;

import java.time.LocalTime;

/**
 * How simulated drivers behave. Speeds vary per leg by up to {@code speedJitter}; {@code failureRate} of
 * deliveries fail; the van named {@code lateVan} gets stuck in traffic for {@code lateMinutes} of plan time on the
 * first leg it starts after {@code lateAfter}.
 */
record DriveOptions(long pingIntervalMillis, double speedJitter, double failureRate, String lateVan, int lateMinutes,
                    LocalTime lateAfter) {
}
