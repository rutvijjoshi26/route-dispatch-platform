package com.routedispatch.optimization;

import static com.routedispatch.optimization.TestRoutes.H;
import static com.routedispatch.optimization.TestRoutes.M;
import static com.routedispatch.optimization.TestRoutes.connect;
import static com.routedispatch.optimization.TestRoutes.vehicle;
import static com.routedispatch.optimization.TestRoutes.visit;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VisitScheduleTest {

    final List<Location> loc = TestRoutes.locations();

    @Test
    void arrivalIsShiftStartPlusTravelAndNextLegStartsAfterService() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        Visit b = visit("b", loc.get(2), 1, 8 * H, 12 * H);

        connect(van, a, b);

        assertThat(a.getArrivalTime()).isEqualTo(8 * H + 600);
        assertThat(a.departureTime()).isEqualTo(8 * H + 600 + 5 * M);
        assertThat(b.getArrivalTime()).isEqualTo(8 * H + 600 + 5 * M + 300);
    }

    @Test
    void earlyArrivalWaitsForWindowStartBeforeService() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 10 * H, 12 * H);

        connect(van, a);

        assertThat(a.getArrivalTime()).isEqualTo(8 * H + 600);
        assertThat(a.serviceStartTime()).isEqualTo(10 * H);
        assertThat(a.departureTime()).isEqualTo(10 * H + 5 * M);
        assertThat(a.waitingSeconds()).isEqualTo(10 * H - (8 * H + 600));
    }

    @Test
    void fixedTimesOfVisitedStopsOverrideTheComputedSchedule() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit done = visit("done", loc.get(1), 1, 8 * H, 12 * H);
        done.fix(9 * H, 9 * H + 10 * M);
        Visit next = visit("next", loc.get(2), 1, 8 * H, 12 * H);

        connect(van, done, next);

        assertThat(done.getArrivalTime()).isEqualTo(9 * H);
        assertThat(next.getArrivalTime()).isEqualTo(9 * H + 10 * M + 300);
    }

    @Test
    void availableFromDelaysTheNextUnvisitedLeg() {
        VehicleRoute van = new VehicleRoute("1", "Van 1", 10, loc.get(0), 8 * H, 17 * H, 11 * H);
        Visit done = visit("done", loc.get(1), 1, 8 * H, 12 * H);
        done.fix(9 * H, 9 * H + 10 * M);
        Visit next = visit("next", loc.get(2), 1, 8 * H, 12 * H);

        connect(van, done, next);

        // Re-planning at 11:00: the driver cannot leave the last stop before "now".
        assertThat(next.getArrivalTime()).isEqualTo(11 * H + 300);
    }

    @Test
    void vehicleTotalsIncludeTheReturnToDepot() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 2, 8 * H, 12 * H);
        Visit c = visit("c", loc.get(3), 3, 8 * H, 12 * H);

        connect(van, a, c);

        assertThat(van.totalDemand()).isEqualTo(5);
        assertThat(van.totalTravelSeconds()).isEqualTo(600 + 700 + 1200);
        assertThat(van.totalMeters()).isEqualTo((600 + 700 + 1200) * 10);
        assertThat(van.returnTime()).isEqualTo(c.departureTime() + 1200);
    }

    @Test
    void emptyVehicleStaysAtDepot() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        connect(van);

        assertThat(van.totalTravelSeconds()).isZero();
        assertThat(van.returnTime()).isEqualTo(8 * H);
    }
}
