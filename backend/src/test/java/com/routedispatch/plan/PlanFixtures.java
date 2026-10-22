package com.routedispatch.plan;

import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.routing.GeoPoint;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** In-memory entities with ids, for unit tests that do not touch the database. */
final class PlanFixtures {

    static final Depot DEPOT = withId(new Depot("Depot", "2500 W Carroll Ave", new GeoPoint(41.8866, -87.6886)), 1L);

    static Vehicle van(long id, LocalTime shiftStart, LocalTime shiftEnd) {
        return withId(new Vehicle(DEPOT, "Van " + id, 50, shiftStart, shiftEnd), id);
    }

    static DispatchPlan plan() {
        return withId(new DispatchPlan("Test day", LocalDate.of(2026, 10, 26), DEPOT), 7L);
    }

    static Stop stop(long id, LocalTime windowStart, LocalTime windowEnd) {
        Stop stop = Stop.newOrder(plan(), "CHI-" + id, "Customer " + id, id + " N State St",
                new GeoPoint(41.88 + id * 0.001, -87.65), windowStart, windowEnd, PackageSize.SMALL, false);
        return withId(stop, id);
    }

    static Stop assigned(long id, long vehicleId, int sequence, LocalTime arrival) {
        Stop stop = stop(id, LocalTime.of(9, 0), LocalTime.of(17, 0));
        stop.assign(vehicleId, sequence, arrival, arrival.plusMinutes(3), List.of());
        return stop;
    }

    static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private PlanFixtures() {
    }
}
