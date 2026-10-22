package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routedispatch.common.ConflictException;
import com.routedispatch.fleet.Depot;
import com.routedispatch.routing.GeoPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PlanClockTest {

    static final Instant DISPATCHED = Instant.parse("2026-10-26T13:00:00Z");

    static DispatchPlan dispatchedPlan(double scale) {
        DispatchPlan plan = new DispatchPlan("Mon", LocalDate.of(2026, 10, 26), new Depot("d", "a", new GeoPoint(41.88, -87.68)));
        plan.startPlanning();
        plan.recordSolution("0hard/0medium/0soft", 0, 0);
        plan.dispatch(DISPATCHED, LocalTime.of(8, 0), scale);
        return plan;
    }

    @Test
    void acceleratedClockAdvancesByTheTimeScale() {
        PlanClock clock = new PlanClock(Clock.fixed(DISPATCHED.plusSeconds(120), ZoneOffset.UTC));

        assertThat(clock.now(dispatchedPlan(30))).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void realTimeClockAdvancesOneToOne() {
        PlanClock clock = new PlanClock(Clock.fixed(DISPATCHED.plusSeconds(90 * 60), ZoneOffset.UTC));

        assertThat(clock.now(dispatchedPlan(1))).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void convertsAnyInstantNotOnlyNow() {
        PlanClock clock = new PlanClock(Clock.systemUTC());

        assertThat(clock.at(dispatchedPlan(60), DISPATCHED.plusSeconds(30))).isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    void eventsFromBeforeDispatchMapToTheClockOrigin() {
        PlanClock clock = new PlanClock(Clock.systemUTC());

        assertThat(clock.at(dispatchedPlan(30), DISPATCHED.minusSeconds(60))).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void clockStopsAtTheEndOfTheServiceDay() {
        PlanClock clock = new PlanClock(Clock.fixed(DISPATCHED.plusSeconds(3600), ZoneOffset.UTC));

        assertThat(clock.now(dispatchedPlan(600))).isEqualTo(LocalTime.of(23, 59, 59));
    }

    @Test
    void planThatIsNotDispatchedHasNoClock() {
        DispatchPlan plan = new DispatchPlan("Mon", LocalDate.of(2026, 10, 26), new Depot("d", "a", new GeoPoint(41.88, -87.68)));

        assertThatThrownBy(() -> new PlanClock(Clock.systemUTC()).now(plan)).isInstanceOf(ConflictException.class);
    }
}
