package com.routedispatch.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.simulator.ApiModels.DriverRoute;
import com.routedispatch.simulator.ApiModels.RouteStop;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteFollowerTest {

    static RouteStop stop(long id, int sequence, String status, String arrival, String departure) {
        return new RouteStop(id, "CHI-" + id, sequence, status, LocalTime.parse("09:00"), LocalTime.parse("17:00"), 180,
                LocalTime.parse(arrival), LocalTime.parse(departure), List.of(new double[] {41.9, -87.7}, new double[] {41.91, -87.7}));
    }

    static DriverRoute route(RouteStop... stops) {
        return new DriverRoute(1, 3, 7, "Van 7", 41.88, -87.68, null, LocalTime.parse("08:00"), List.of(stops), List.of());
    }

    @Test
    void nextStopIsTheFirstNotFinishedInSequence() {
        DriverRoute route = route(stop(1, 0, "DELIVERED", "08:20", "08:23"), stop(2, 1, "FAILED", "08:40", "08:43"),
                stop(3, 2, "PENDING", "09:00", "09:03"), stop(4, 3, "PENDING", "09:30", "09:33"));

        assertThat(RouteFollower.nextStop(route)).hasValueSatisfying(s -> assertThat(s.id()).isEqualTo(3));
    }

    @Test
    void noNextStopWhenEverythingIsDone() {
        assertThat(RouteFollower.nextStop(route(stop(1, 0, "DELIVERED", "08:20", "08:23")))).isEmpty();
    }

    @Test
    void plannedLegDurationRunsFromPreviousDepartureOrDepot() {
        DriverRoute route = route(stop(1, 0, "DELIVERED", "08:20", "08:23"), stop(2, 1, "PENDING", "08:40", "08:43"));

        assertThat(RouteFollower.plannedLegSeconds(route, route.stops().get(0))).isEqualTo(20 * 60);
        assertThat(RouteFollower.plannedLegSeconds(route, route.stops().get(1))).isEqualTo(17 * 60);
    }

    @Test
    void legDurationHasAFloorSoZeroLengthLegsStillTakeTime() {
        DriverRoute route = route(stop(1, 0, "PENDING", "08:00", "08:03"));

        assertThat(RouteFollower.plannedLegSeconds(route, route.stops().getFirst())).isEqualTo(30);
    }
}
