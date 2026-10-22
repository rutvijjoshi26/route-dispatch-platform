package com.routedispatch.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.routedispatch.demo.ChicagoDayGenerator;
import com.routedispatch.demo.GeneratedDay;
import com.routedispatch.demo.GeneratedOrder;
import com.routedispatch.demo.GeneratedVehicle;
import com.routedispatch.optimization.OptimizationResult;
import com.routedispatch.optimization.RouteOptimizer;
import com.routedispatch.optimization.RoutingProblemFactory;
import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.ScoreUpdate;
import com.routedispatch.optimization.SolveMode;
import com.routedispatch.optimization.SolverSettings;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.Visit;
import com.routedispatch.optimization.VisitSpec;
import com.routedispatch.optimization.baseline.NearestNeighborPlanner;
import com.routedispatch.optimization.baseline.RouteMetrics;
import com.routedispatch.optimization.baseline.RoundRobinPlanner;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.HaversineTravelTimeProvider;
import com.routedispatch.routing.OsrmTravelTimeProvider;
import com.routedispatch.routing.TravelMatrixService;
import com.routedispatch.routing.TravelTimeProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.web.client.RestClient;

/**
 * Offline routing benchmark on the seeded Chicago day. Compares round-robin, nearest-neighbor and Timefold on
 * the same road matrix, records solver convergence, and measures single-order re-plan latency with pinning.
 *
 * <p>Usage: {@code ./gradlew :backend:benchmark -PbenchmarkArgs="--osrm-url http://localhost:5001"}
 */
public final class BenchmarkApp {

    private static final long H = 3600;

    public static void main(String[] args) throws IOException {
        BenchmarkOptions options = BenchmarkOptions.parse(args);
        new BenchmarkApp().run(options);
    }

    void run(BenchmarkOptions options) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", OffsetDateTime.now().toString());
        report.put("hardware", Hardware.describe());
        report.put("options", options);

        GeneratedDay day = ChicagoDayGenerator.generate(options.seed(), options.stops(), options.vans());
        TravelTimeProvider provider = options.provider().equals("haversine")
                ? new HaversineTravelTimeProvider(1.35, 28)
                : new OsrmTravelTimeProvider(RestClient.builder().baseUrl(options.osrmUrl()).build(), 100);
        TravelMatrixService matrixService = new TravelMatrixService(provider, 2_000_000);
        RoutingProblemFactory factory = new RoutingProblemFactory(matrixService);
        List<VehicleSpec> vans = vehicles(day);
        List<VisitSpec> visits = visits(day.orders());

        long matrixStart = System.nanoTime();
        RoutingSolution template = factory.create(vans, visits);
        long matrixMillis = millisSince(matrixStart);
        report.put("matrix", Map.of("locations", visits.size() + 1, "pairs", (long) (visits.size() + 1) * (visits.size() + 1),
                "provider", provider.name(), "buildMillis", matrixMillis));
        log("Matrix of %d locations from %s in %d ms", visits.size() + 1, provider.name(), matrixMillis);

        RouteOptimizer optimizer = new RouteOptimizer(
                new SolverSettings(Duration.ofSeconds(options.solveSeconds()), Duration.ofSeconds(options.solveSeconds())),
                new SolverSettings(Duration.ofSeconds(4), Duration.ofSeconds(1)));

        RouteMetrics roundRobin = RouteMetrics.of(optimizer.evaluate(new RoundRobinPlanner().plan(factory.create(vans, visits))));
        RouteMetrics nearest = RouteMetrics.of(optimizer.evaluate(new NearestNeighborPlanner().plan(factory.create(vans, visits))));
        log("Round-robin: %s", summary(roundRobin));
        log("Nearest-neighbor: %s", summary(nearest));

        log("Timefold solving for %d s ...", options.solveSeconds());
        OptimizationResult solved = optimizer.optimize(template, SolveMode.INITIAL, update -> { });
        RouteMetrics timefold = RouteMetrics.of(solved.solution());
        log("Timefold: %s (score %s)", summary(timefold), solved.solution().getScore());

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("note", "One seeded day (seed " + options.seed() + "), one run. Round-robin is a deliberately naive "
                + "baseline that ignores capacity, windows and shifts; nearest-neighbor is greedy but feasibility-aware. "
                + "Compare distances together with each plan's on-time, unassigned, overflow and overrun figures.");
        quality.put("timefoldFeasible", timefold.unassignedStops() == 0 && timefold.lateStops() == 0
                && timefold.capacityOverflowUnits() == 0 && timefold.shiftOverrunSeconds() == 0);
        quality.put("roundRobin", roundRobin);
        quality.put("nearestNeighbor", nearest);
        quality.put("timefold", timefold);
        quality.put("timefoldScore", solved.solution().getScore().toString());
        quality.put("distanceReductionVsRoundRobinPct", pct(roundRobin.totalMeters(), timefold.totalMeters()));
        quality.put("distanceReductionVsNearestNeighborPct", pct(nearest.totalMeters(), timefold.totalMeters()));
        quality.put("driveTimeReductionVsRoundRobinPct", pct(roundRobin.totalTravelSeconds(), timefold.totalTravelSeconds()));
        quality.put("driveTimeReductionVsNearestNeighborPct", pct(nearest.totalTravelSeconds(), timefold.totalTravelSeconds()));
        report.put("quality", quality);
        report.put("convergence", convergence(solved));

        report.put("replan", replanBenchmark(options, day, factory, optimizer, solved.solution()));

        Path out = Path.of(options.outDir());
        Files.createDirectories(out);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(out.resolve("routing-benchmark.json").toFile(), report);
        log("Wrote %s", out.resolve("routing-benchmark.json").toAbsolutePath());
    }

    private Map<String, Object> convergence(OptimizationResult solved) {
        List<ScoreUpdate> timeline = solved.timeline();
        ScoreUpdate last = timeline.getLast();
        Long firstFeasible = null;
        Long withinOnePct = null;
        for (ScoreUpdate update : timeline) {
            boolean feasible = update.score().hardScore() == 0 && update.score().mediumScore() == 0;
            if (feasible && firstFeasible == null) {
                firstFeasible = update.elapsedMillis();
            }
            if (feasible && withinOnePct == null
                    && update.score().softScore() >= last.score().softScore() * 1.01) {
                withinOnePct = update.elapsedMillis();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSolveMillis", solved.elapsedMillis());
        result.put("bestScoreUpdates", timeline.size());
        result.put("firstFeasibleMillis", firstFeasible);
        result.put("withinOnePercentOfFinalMillis", withinOnePct);
        result.put("finalScore", last.score().toString());
        List<Map<String, Object>> checkpoints = new ArrayList<>();
        for (long checkpoint : new long[] {1_000, 2_000, 5_000, 10_000, 20_000, 30_000, 60_000, 120_000}) {
            if (checkpoint > solved.elapsedMillis()) {
                break;
            }
            ScoreUpdate best = null;
            for (ScoreUpdate update : timeline) {
                if (update.elapsedMillis() <= checkpoint) {
                    best = update;
                }
            }
            if (best != null) {
                checkpoints.add(Map.of("atMillis", checkpoint, "score", best.score().toString(),
                        "driveSeconds", -best.score().softScore()));
            }
        }
        result.put("checkpoints", checkpoints);
        return result;
    }

    /**
     * Freezes the optimized plan at 12:00 (stops reached before noon are done, the next stop on each van is in
     * progress), inserts one new order, and re-plans. Repeated with different new orders.
     */
    private Map<String, Object> replanBenchmark(BenchmarkOptions options, GeneratedDay day, RoutingProblemFactory factory,
                                                RouteOptimizer optimizer, RoutingSolution planned) {
        long now = 12 * H;
        Random random = new Random(options.seed() + 1);
        List<Long> totals = new ArrayList<>();
        List<Long> matrixTimes = new ArrayList<>();
        List<Long> solveTimes = new ArrayList<>();
        int pinnedTotal = 0;
        int insertedOnTime = 0;
        boolean pinsRespected = true;
        List<VisitSpec> baseVisits = visits(day.orders());
        for (int run = 0; run < options.replanRuns(); run++) {
            List<VehicleSpec> vans = new ArrayList<>();
            Map<String, long[]> fixed = new LinkedHashMap<>();
            Map<String, List<String>> pinnedByVan = new LinkedHashMap<>();
            for (VehicleRoute route : planned.getVehicles()) {
                List<String> order = route.getVisits().stream().map(Visit::getId).toList();
                int pinned = 0;
                for (Visit visit : route.getVisits()) {
                    boolean reached = visit.getArrivalTime() < now;
                    if (reached || pinned == countReached(route, now)) {
                        fixed.put(visit.getId(), new long[] {visit.getArrivalTime(), visit.departureTime()});
                        pinned++;
                        if (!reached) {
                            break;
                        }
                    }
                }
                pinnedByVan.put(route.getId(), order.subList(0, pinned));
                pinnedTotal += pinned;
                vans.add(new VehicleSpec(route.getId(), route.getName(), route.getCapacity(), day.depot().point(),
                        route.getShiftStart(), route.getShiftEnd(), now, order, pinned));
            }
            List<VisitSpec> replanVisits = new ArrayList<>();
            for (VisitSpec spec : baseVisits) {
                long[] times = fixed.get(spec.id());
                Visit plannedVisit = planned.getVisits().stream().filter(v -> v.getId().equals(spec.id())).findFirst().orElseThrow();
                VisitSpec withEta = spec.withCommittedEta(plannedVisit.getArrivalTime());
                replanVisits.add(times == null ? withEta : withEta.withFixedTimes(times[0], times[1]));
            }
            GeoPoint point = new GeoPoint(41.86 + random.nextDouble() * 0.10, -87.73 + random.nextDouble() * 0.08);
            replanVisits.add(VisitSpec.of("NEW-" + run, point, 2, 13 * H, 17 * H, 240));

            long start = System.nanoTime();
            RoutingSolution problem = factory.create(vans, replanVisits);
            long matrixMillis = millisSince(start);
            OptimizationResult result = optimizer.optimize(problem, SolveMode.REPLAN, u -> { });
            long total = millisSince(start);
            totals.add(total);
            matrixTimes.add(matrixMillis);
            solveTimes.add(result.elapsedMillis());

            for (VehicleRoute route : result.solution().getVehicles()) {
                List<String> expected = pinnedByVan.get(route.getId());
                List<String> actual = route.getVisits().stream().limit(expected.size()).map(Visit::getId).toList();
                pinsRespected &= actual.equals(expected);
            }
            Visit inserted = result.solution().getVisits().stream().filter(v -> v.getId().startsWith("NEW-")).findFirst().orElseThrow();
            if (inserted.getVehicle() != null && inserted.secondsLate() == 0) {
                insertedOnTime++;
            }
            log("Re-plan run %d: matrix + solve %d ms (matrix %d ms, solver %d ms), score %s", run + 1, total, matrixMillis,
                    result.elapsedMillis(), result.solution().getScore());
        }
        Map<String, Object> replan = new LinkedHashMap<>();
        replan.put("runs", options.replanRuns());
        replan.put("replanTermination", "spent 4s or 1s unimproved");
        replan.put("frozenAt", "12:00");
        replan.put("avgPinnedStopsPerRun", (double) pinnedTotal / options.replanRuns());
        replan.put("pinnedPrefixesUnchanged", pinsRespected);
        replan.put("insertedOrderOnTimeRuns", insertedOnTime);
        replan.put("scope", "In-process only: incremental travel matrix (one new location, real OSRM calls) plus solve. "
                + "The API pipeline (snapshot, road geometry, database save, push) is measured by the simulator's replan mode.");
        replan.put("matrixPlusSolveMillis", Stats.of(totals));
        replan.put("matrixMillis", Stats.of(matrixTimes));
        replan.put("solverMillis", Stats.of(solveTimes));
        return replan;
    }

    private static int countReached(VehicleRoute route, long now) {
        int count = 0;
        for (Visit visit : route.getVisits()) {
            if (visit.getArrivalTime() < now) {
                count++;
            }
        }
        return count;
    }

    static List<VehicleSpec> vehicles(GeneratedDay day) {
        List<VehicleSpec> vans = new ArrayList<>();
        for (int i = 0; i < day.vehicles().size(); i++) {
            GeneratedVehicle v = day.vehicles().get(i);
            vans.add(VehicleSpec.of("van-" + (i + 1), v.name(), v.capacity(), day.depot().point(),
                    v.shiftStart().toSecondOfDay(), v.shiftEnd().toSecondOfDay()));
        }
        return vans;
    }

    static List<VisitSpec> visits(List<GeneratedOrder> orders) {
        return orders.stream()
                .map(o -> VisitSpec.of(o.orderRef(), o.point(), o.size().units(), o.windowStart().toSecondOfDay(),
                        o.windowEnd().toSecondOfDay(), o.size().serviceSeconds()))
                .toList();
    }

    private static double pct(long baseline, long optimized) {
        return Math.round(1000.0 * (baseline - optimized) / baseline) / 10.0;
    }

    private static String summary(RouteMetrics m) {
        return "%.1f km, %.1f h driving, on time %d/%d (%.1f%%), unassigned %d, capacity overflow %d, shift overrun %d min"
                .formatted(m.totalMeters() / 1000.0, m.totalTravelSeconds() / 3600.0, m.onTimeStops(), m.stops(),
                        100 * m.onTimeRate(), m.unassignedStops(), m.capacityOverflowUnits(), m.shiftOverrunSeconds() / 60);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void log(String format, Object... args) {
        System.out.println("[benchmark] " + format.formatted(args));
    }
}
