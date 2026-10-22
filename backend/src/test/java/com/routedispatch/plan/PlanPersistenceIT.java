package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PlanPersistenceIT {

    @Autowired DepotRepository depots;
    @Autowired VehicleRepository vehicles;
    @Autowired DispatchPlanRepository plans;
    @Autowired StopRepository stops;
    @Autowired RouteRepository routes;
    @Autowired JdbcTemplate jdbc;

    @Test
    void wallClockTimesAreStoredWithoutTimeZoneShift() {
        Depot depot = depots.save(new Depot("Clock depot", "1 N State St, Chicago, IL", new GeoPoint(41.88, -87.63)));
        Vehicle late = vehicles.save(new Vehicle(depot, "Evening Van " + System.nanoTime(), 30, LocalTime.of(12, 0), LocalTime.of(20, 30)));

        String stored = jdbc.queryForObject("SELECT shift_end::text FROM vehicle WHERE id = ?", String.class, late.getId());
        assertThat(stored).isEqualTo("20:30:00");
        assertThat(vehicles.findById(late.getId()).orElseThrow().getShiftEnd()).isEqualTo(LocalTime.of(20, 30));
    }

    @Test
    void stopsAndRoutesRoundTripThroughPostgisGeography() {
        Depot depot = depots.save(new Depot("Test depot", "2500 W Carroll Ave, Chicago, IL", new GeoPoint(41.8866, -87.6886)));
        Vehicle van = vehicles.save(new Vehicle(depot, "Persistence Van", 40, LocalTime.of(8, 0), LocalTime.of(16, 0)));
        DispatchPlan plan = plans.save(new DispatchPlan("Monday", LocalDate.of(2026, 10, 26), depot));
        Stop stop = Stop.newOrder(plan, "CHI-1", "Maria Garcia", "1600 W Fullerton Ave, Chicago, IL",
                new GeoPoint(41.925376, -87.668033), LocalTime.of(9, 0), LocalTime.of(11, 0), PackageSize.MEDIUM, false);
        stop.assign(van.getId(), 0, LocalTime.of(9, 12), LocalTime.of(9, 16),
                List.of(depot.getLocation(), new GeoPoint(41.90, -87.68), stop.getLocation()));
        stop = stops.save(stop);
        Route route = new Route(plan, van.getId());
        route.updateGeometry(List.of(depot.getLocation(), stop.getLocation(), depot.getLocation()),
                List.of(stop.getLocation(), depot.getLocation()));
        route.updateTotals(9_000, 1_500, LocalTime.of(8, 0), LocalTime.of(9, 40), 2);
        routes.save(route);

        Stop loaded = stops.findById(stop.getId()).orElseThrow();
        assertThat(loaded.getLocation()).isEqualTo(new GeoPoint(41.925376, -87.668033));
        assertThat(loaded.getInboundLeg()).hasSize(3).last().isEqualTo(stop.getLocation());
        assertThat(loaded.getDemand()).isEqualTo(2);
        Route loadedRoute = routes.findByPlanIdAndVehicleId(plan.getId(), van.getId()).orElseThrow();
        assertThat(loadedRoute.getPath()).hasSize(3);
        assertThat(loadedRoute.getReturnLeg()).hasSize(2);

        String type = jdbc.queryForObject(
                "SELECT GeometryType(location::geometry) || ':' || ST_SRID(location::geometry) FROM stop WHERE id = ?",
                String.class, stop.getId());
        assertThat(type).isEqualTo("POINT:4326");
        Double meters = jdbc.queryForObject(
                "SELECT ST_Distance(s.location, d.location) FROM stop s JOIN depot d ON d.id = ? WHERE s.id = ?",
                Double.class, depot.getId(), stop.getId());
        assertThat(meters).isBetween(4_500.0, 5_500.0);
    }
}
