package com.routedispatch.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.optimization.baseline.NearestNeighborPlanner;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.HaversineTravelTimeProvider;
import com.routedispatch.routing.TravelMatrixService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RouteOptimizerTest {

    static final long H = 3600;
    static final GeoPoint DEPOT = new GeoPoint(41.8868, -87.6870);

    final RoutingProblemFactory factory =
            new RoutingProblemFactory(new TravelMatrixService(new HaversineTravelTimeProvider(1.35, 28), 100_000));
    final RouteOptimizer optimizer = new RouteOptimizer(
            new SolverSettings(Duration.ofSeconds(4), Duration.ofMillis(800)),
            new SolverSettings(Duration.ofSeconds(2), Duration.ofMillis(400)));

    static List<VisitSpec> visits(int count, long seed) {
        Random random = new Random(seed);
        List<VisitSpec> visits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GeoPoint point = new GeoPoint(41.82 + random.nextDouble() * 0.14, -87.75 + random.nextDouble() * 0.12);
            boolean morning = i % 3 == 0;
            visits.add(VisitSpec.of("s" + i, point, 1 + random.nextInt(3),
                    morning ? 8 * H : 8 * H, morning ? 13 * H : 17 * H, 240));
        }
        return visits;
    }

    static List<VehicleSpec> vans(int count) {
        List<VehicleSpec> vans = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            vans.add(VehicleSpec.of("v" + i, "Van " + i, 30, DEPOT, 8 * H, 18 * H));
        }
        return vans;
    }

    @Test
    void initialSolveAssignsEveryVisitWithoutHardViolations() {
        RoutingSolution problem = factory.create(vans(3), visits(30, 7));

        OptimizationResult result = optimizer.optimize(problem, SolveMode.INITIAL, update -> { });

        assertThat(result.solution().getScore().hardScore()).isZero();
        assertThat(result.solution().getScore().mediumScore()).isZero();
        int assigned = result.solution().getVehicles().stream().mapToInt(v -> v.getVisits().size()).sum();
        assertThat(assigned).isEqualTo(30);
        assertThat(result.timeline()).isNotEmpty();
        assertThat(result.elapsedMillis()).isLessThan(10_000);
    }

    @Test
    void replanKeepsThePinnedPrefixAndInsertsTheNewOrder() {
        List<VisitSpec> visits = visits(24, 11);
        OptimizationResult first = optimizer.optimize(factory.create(vans(3), visits), SolveMode.INITIAL, u -> { });
        RoutingSolution planned = first.solution();

        // Mid-shift at 11:00: the busiest van has completed its first two stops and is driving to the third.
        VehicleRoute v1 = planned.getVehicles().stream()
                .max(java.util.Comparator.comparingInt(v -> v.getVisits().size())).orElseThrow();
        List<String> v1Order = v1.getVisits().stream().map(Visit::getId).toList();
        assertThat(v1Order).hasSizeGreaterThanOrEqualTo(3);
        List<String> pinned = v1Order.subList(0, 3);

        Map<String, VisitSpec> specs = byId(visits, VisitSpec::id);
        List<VisitSpec> replanVisits = new ArrayList<>();
        for (VisitSpec spec : visits) {
            int pinIndex = pinned.indexOf(spec.id());
            replanVisits.add(pinIndex < 0 ? spec : spec.withFixedTimes(9 * H + pinIndex * 1800L, 9 * H + pinIndex * 1800L + 600));
        }
        VisitSpec newOrder = VisitSpec.of("new", new GeoPoint(41.90, -87.70), 2, 11 * H, 16 * H, 300);
        replanVisits.add(newOrder);

        List<VehicleSpec> replanVans = new ArrayList<>();
        for (VehicleRoute route : planned.getVehicles()) {
            List<String> order = route.getVisits().stream().map(Visit::getId).toList();
            VehicleSpec base = VehicleSpec.of(route.getId(), route.getName(), 30, DEPOT, 8 * H, 18 * H);
            replanVans.add(base.withState(11 * H, order, route.getId().equals(v1.getId()) ? 3 : 0));
        }
        assertThat(specs).doesNotContainKey("new");

        OptimizationResult replanned =
                optimizer.optimize(factory.create(replanVans, replanVisits), SolveMode.REPLAN, u -> { });

        VehicleRoute newV1 = byId(replanned.solution().getVehicles(), VehicleRoute::getId).get(v1.getId());
        assertThat(newV1.getVisits().subList(0, 3).stream().map(Visit::getId).toList()).isEqualTo(pinned);
        Visit inserted = byId(replanned.solution().getVisits(), Visit::getId).get("new");
        assertThat(inserted.getVehicle()).isNotNull();
        assertThat(inserted.getArrivalTime()).isGreaterThanOrEqualTo(11 * H);
        assertThat(replanned.solution().getScore().mediumScore()).isZero();
        assertThat(replanned.elapsedMillis()).isLessThan(5_000);
    }

    @Test
    void initialSolveIsNeverWorseThanTheGreedySeedEvenWithATinyBudget() {
        RouteOptimizer hurried = new RouteOptimizer(
                new SolverSettings(Duration.ofMillis(100), Duration.ofMillis(100)),
                new SolverSettings(Duration.ofMillis(100), Duration.ofMillis(100)));
        RoutingSolution greedy = hurried.evaluate(
                new NearestNeighborPlanner().plan(factory.create(vans(4), visits(120, 5))));

        OptimizationResult result = hurried.optimize(factory.create(vans(4), visits(120, 5)), SolveMode.INITIAL, u -> { });

        assertThat(result.solution().getScore().mediumScore()).isZero();
        assertThat(result.solution().getScore()).isGreaterThanOrEqualTo(greedy.getScore());
    }

    @Test
    void evaluateComputesScheduleAndScoreForAFixedAssignment() {
        RoutingSolution problem = factory.create(
                List.of(VehicleSpec.of("v1", "Van 1", 30, DEPOT, 8 * H, 18 * H).withState(8 * H, List.of("s0", "s1"), 0)),
                visits(2, 3));

        RoutingSolution evaluated = optimizer.evaluate(problem);

        assertThat(evaluated.getScore()).isNotNull();
        assertThat(evaluated.getVisits()).allSatisfy(v -> assertThat(v.getArrivalTime()).isNotNull());
        assertThat(evaluated.getScore().softScore()).isEqualTo(-evaluated.getVehicles().getFirst().totalTravelSeconds());
    }

    static <T> Map<String, T> byId(List<T> items, Function<T, String> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
