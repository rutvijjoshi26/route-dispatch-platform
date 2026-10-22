package com.routedispatch.simulator;

import com.routedispatch.simulator.ApiModels.DriverRoute;
import com.routedispatch.simulator.ApiModels.RouteStop;
import java.time.LocalTime;
import java.util.Optional;

/** Decisions a driver makes from the current route: where to go next and how long the drive should take. */
final class RouteFollower {

    static final int MIN_LEG_SECONDS = 30;

    static Optional<RouteStop> nextStop(DriverRoute route) {
        return route.stops().stream().filter(s -> !s.isFinished()).findFirst();
    }

    /** Planned driving time into {@code stop}: from the previous stop's planned departure (or the depot departure). */
    static long plannedLegSeconds(DriverRoute route, RouteStop stop) {
        LocalTime from = route.departDepot();
        for (RouteStop candidate : route.stops()) {
            if (candidate.id() == stop.id()) {
                break;
            }
            from = candidate.plannedDeparture();
        }
        if (from == null || stop.plannedArrival() == null) {
            return MIN_LEG_SECONDS;
        }
        return Math.max(MIN_LEG_SECONDS, stop.plannedArrival().toSecondOfDay() - from.toSecondOfDay());
    }

    private RouteFollower() {
    }
}
