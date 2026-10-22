package com.routedispatch.optimization.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.optimization.RouteOptimizer;
import com.routedispatch.optimization.RoutingProblemFactory;
import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.SolverSettings;
import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.VisitSpec;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.HaversineTravelTimeProvider;
import com.routedispatch.routing.TravelMatrixService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteMetricsTest {

    static final long H = 3600;
    static final GeoPoint DEPOT = new GeoPoint(41.88, -87.70);

    @Test
    void countsOnTimeLateAndUnassignedStops() {
        RoutingProblemFactory factory =
                new RoutingProblemFactory(new TravelMatrixService(new HaversineTravelTimeProvider(1.3, 30), 10_000));
        RouteOptimizer optimizer = new RouteOptimizer(
                new SolverSettings(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new SolverSettings(Duration.ofSeconds(1), Duration.ofSeconds(1)));
        List<VisitSpec> visits = List.of(
                VisitSpec.of("onTime", new GeoPoint(41.89, -87.70), 1, 8 * H, 12 * H, 300),
                // window closes one minute after shift start: unreachable in time
                VisitSpec.of("late", new GeoPoint(41.95, -87.70), 1, 8 * H, 8 * H + 60, 300),
                VisitSpec.of("unassigned", new GeoPoint(41.90, -87.70), 1, 8 * H, 12 * H, 300));
        RoutingSolution solution = factory.create(
                List.of(VehicleSpec.of("a", "A", 10, DEPOT, 8 * H, 17 * H).withState(8 * H, List.of("onTime", "late"), 0)),
                visits);

        RouteMetrics metrics = RouteMetrics.of(optimizer.evaluate(solution));

        assertThat(metrics.stops()).isEqualTo(3);
        assertThat(metrics.onTimeStops()).isEqualTo(1);
        assertThat(metrics.lateStops()).isEqualTo(1);
        assertThat(metrics.unassignedStops()).isEqualTo(1);
        assertThat(metrics.onTimeRate()).isEqualTo(1.0 / 3);
        assertThat(metrics.vehiclesUsed()).isEqualTo(1);
        assertThat(metrics.totalMeters()).isEqualTo(solution.getVehicles().getFirst().totalMeters());
    }
}
