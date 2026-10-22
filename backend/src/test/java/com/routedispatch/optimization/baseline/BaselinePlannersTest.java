package com.routedispatch.optimization.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.optimization.RoutingProblemFactory;
import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.Visit;
import com.routedispatch.optimization.VisitSpec;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.HaversineTravelTimeProvider;
import com.routedispatch.routing.TravelMatrixService;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselinePlannersTest {

    static final long H = 3600;
    static final GeoPoint DEPOT = new GeoPoint(41.88, -87.70);

    final RoutingProblemFactory factory =
            new RoutingProblemFactory(new TravelMatrixService(new HaversineTravelTimeProvider(1.3, 30), 10_000));

    static VisitSpec stop(String id, double lat, double lon, int demand) {
        return VisitSpec.of(id, new GeoPoint(lat, lon), demand, 8 * H, 17 * H, 300);
    }

    static List<String> ids(VehicleRoute route) {
        return route.getVisits().stream().map(Visit::getId).toList();
    }

    @Test
    void roundRobinDealsStopsInEntryOrder() {
        RoutingSolution problem = factory.create(
                List.of(VehicleSpec.of("a", "A", 10, DEPOT, 8 * H, 17 * H), VehicleSpec.of("b", "B", 10, DEPOT, 8 * H, 17 * H)),
                List.of(stop("1", 41.90, -87.70, 1), stop("2", 41.91, -87.70, 1), stop("3", 41.92, -87.70, 1)));

        new RoundRobinPlanner().plan(problem);

        assertThat(ids(problem.getVehicles().get(0))).containsExactly("1", "3");
        assertThat(ids(problem.getVehicles().get(1))).containsExactly("2");
    }

    @Test
    void nearestNeighborAlwaysDrivesToTheClosestRemainingStop() {
        RoutingSolution problem = factory.create(
                List.of(VehicleSpec.of("a", "A", 10, DEPOT, 8 * H, 17 * H)),
                List.of(stop("far", 41.95, -87.70, 1), stop("near", 41.89, -87.70, 1), stop("mid", 41.92, -87.70, 1)));

        new NearestNeighborPlanner().plan(problem);

        assertThat(ids(problem.getVehicles().get(0))).containsExactly("near", "mid", "far");
    }

    @Test
    void nearestNeighborStartsTheNextVanWhenTheFirstIsFull() {
        RoutingSolution problem = factory.create(
                List.of(VehicleSpec.of("a", "A", 3, DEPOT, 8 * H, 17 * H), VehicleSpec.of("b", "B", 3, DEPOT, 8 * H, 17 * H)),
                List.of(stop("1", 41.89, -87.70, 2), stop("2", 41.90, -87.70, 2), stop("3", 41.91, -87.70, 1)));

        new NearestNeighborPlanner().plan(problem);

        assertThat(ids(problem.getVehicles().get(0))).containsExactly("1", "3");
        assertThat(ids(problem.getVehicles().get(1))).containsExactly("2");
    }

    @Test
    void nearestNeighborStillPlacesStopsNoVanCanServeFeasibly() {
        RoutingSolution problem = factory.create(
                List.of(VehicleSpec.of("a", "A", 2, DEPOT, 8 * H, 17 * H)),
                List.of(stop("1", 41.89, -87.70, 2), stop("2", 41.90, -87.70, 2)));

        new NearestNeighborPlanner().plan(problem);

        assertThat(ids(problem.getVehicles().get(0))).containsExactly("1", "2");
    }
}
