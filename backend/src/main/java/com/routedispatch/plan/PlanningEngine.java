package com.routedispatch.plan;

import com.routedispatch.common.NotFoundException;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.optimization.OptimizationResult;
import com.routedispatch.optimization.RouteOptimizer;
import com.routedispatch.optimization.RoutingProblemFactory;
import com.routedispatch.optimization.RoutingSolution;
import com.routedispatch.optimization.SolveMode;
import com.routedispatch.optimization.VehicleRoute;
import com.routedispatch.optimization.Visit;
import com.routedispatch.plan.PlanRevision.Reason;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.RouteGeometry;
import com.routedispatch.routing.TravelMatrixService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one solve or re-plan end to end:
 * <ol>
 *   <li>snapshot the plan (short transaction), pinning what drivers already did,</li>
 *   <li>solve outside any transaction (travel matrix comes from cache plus incremental OSRM calls),</li>
 *   <li>fetch road geometry for routes whose order changed,</li>
 *   <li>reconcile with driver events that arrived meanwhile and save (short transaction, retried on
 *       optimistic-lock conflicts with concurrent driver events),</li>
 *   <li>publish the new version.</li>
 * </ol>
 * Callers must not run two jobs for the same plan at once; {@link ReplanCoordinator} guarantees that.
 */
@Service
public class PlanningEngine {

    private static final Logger log = LoggerFactory.getLogger(PlanningEngine.class);
    private static final int MAX_SAVE_ATTEMPTS = 3;

    public record Job(Reason reason, String detail) {

        SolveMode mode() {
            return reason == Reason.INITIAL || reason == Reason.REOPTIMIZE ? SolveMode.INITIAL : SolveMode.REPLAN;
        }
    }

    private record Snapshot(PlanSnapshot problem, Map<Long, List<Long>> previousOrder, GeoPoint depot) {
    }

    private record Saved(int version, int changedVehicles, String score) {
    }

    private final DispatchPlanRepository plans;
    private final StopRepository stops;
    private final RouteRepository routes;
    private final PlanRevisionRepository revisions;
    private final VehicleRepository vehicles;
    private final PlanSnapshotMapper snapshotMapper;
    private final RouteReconciler reconciler;
    private final RoutingProblemFactory problemFactory;
    private final RouteOptimizer optimizer;
    private final TravelMatrixService matrixService;
    private final PlanClock clock;
    private final PlanEventPublisher publisher;
    private final TransactionTemplate tx;

    public PlanningEngine(DispatchPlanRepository plans, StopRepository stops, RouteRepository routes,
                          PlanRevisionRepository revisions, VehicleRepository vehicles, PlanSnapshotMapper snapshotMapper,
                          RouteReconciler reconciler, RoutingProblemFactory problemFactory, RouteOptimizer optimizer,
                          TravelMatrixService matrixService, PlanClock clock, PlanEventPublisher publisher,
                          TransactionTemplate tx) {
        this.plans = plans;
        this.stops = stops;
        this.routes = routes;
        this.revisions = revisions;
        this.vehicles = vehicles;
        this.snapshotMapper = snapshotMapper;
        this.reconciler = reconciler;
        this.problemFactory = problemFactory;
        this.optimizer = optimizer;
        this.matrixService = matrixService;
        this.clock = clock;
        this.publisher = publisher;
        this.tx = tx;
    }

    public void run(long planId, Job job) {
        long start = System.nanoTime();
        try {
            Snapshot snapshot = tx.execute(status -> snapshot(planId, job));
            if (snapshot.problem().visits().isEmpty()) {
                tx.executeWithoutResult(status -> plans.findById(planId).ifPresent(DispatchPlan::planningFailed));
                publisher.planFailed(new PlanEvents.PlanFailed(planId, job.reason().name(), "The plan has no orders"));
                return;
            }
            RoutingSolution problem = problemFactory.create(snapshot.problem().vehicles(), snapshot.problem().visits());
            AtomicLong lastProgress = new AtomicLong();
            OptimizationResult result = optimizer.optimize(problem, job.mode(), update -> {
                long now = System.nanoTime();
                if (now - lastProgress.get() > 1_000_000_000L) {
                    lastProgress.set(now);
                    publisher.solveProgress(new PlanEvents.SolveProgress(planId, job.reason().name(),
                            update.elapsedMillis(), update.score().toString()));
                }
            });
            Map<Long, List<Long>> solved = orders(result.solution());
            Map<Long, RouteGeometry> geometry = new HashMap<>();
            solved.forEach((vehicleId, order) -> {
                if (!order.equals(snapshot.previousOrder().getOrDefault(vehicleId, List.of()))) {
                    geometry.put(vehicleId, roadGeometry(snapshot.depot(), order, snapshot.problem()));
                }
            });

            Saved saved = saveWithRetry(planId, job, solved, geometry, result, snapshot, start);
            long totalMillis = (System.nanoTime() - start) / 1_000_000;
            log.info("Plan {} v{} ({}): solver {} ms, total {} ms, {} vans changed, score {}", planId, saved.version(),
                    job.reason(), result.elapsedMillis(), totalMillis, saved.changedVehicles(), saved.score());
            publisher.planUpdated(new PlanEvents.PlanUpdated(planId, saved.version(), job.reason().name(), job.detail(),
                    totalMillis, result.elapsedMillis(), saved.changedVehicles(), snapshot.problem().pinnedStops(),
                    saved.score()));
        } catch (NotFoundException e) {
            log.warn("Plan {} disappeared before it could be planned", planId);
        } catch (RuntimeException e) {
            log.error("Planning failed for plan {} ({})", planId, job.reason(), e);
            tx.executeWithoutResult(status -> plans.findById(planId).ifPresent(DispatchPlan::planningFailed));
            publisher.planFailed(new PlanEvents.PlanFailed(planId, job.reason().name(), e.getMessage()));
        }
    }

    private Snapshot snapshot(long planId, Job job) {
        DispatchPlan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        if (job.mode() == SolveMode.INITIAL && !plan.isLive()) {
            plan.startPlanning();
        }
        PlanState state = state(plan);
        Map<Long, List<Long>> current = PlanState.currentOrder(state.stops());
        return new Snapshot(snapshotMapper.map(state, current), current, state.depot());
    }

    private PlanState state(DispatchPlan plan) {
        List<Vehicle> fleet = vehicles.findByDepotIdOrderById(plan.getDepot().getId());
        Map<Long, LocalTime> departDepot = routes.findByPlanIdOrderByVehicleId(plan.getId()).stream()
                .collect(Collectors.toMap(Route::getVehicleId, Route::getDepartDepot));
        return new PlanState(plan.getDepot().getLocation(), fleet, stops.findByPlanIdOrderById(plan.getId()),
                departDepot, plan.isLive() ? clock.now(plan) : null);
    }

    private Saved saveWithRetry(long planId, Job job, Map<Long, List<Long>> solved, Map<Long, RouteGeometry> geometry,
                                OptimizationResult result, Snapshot snapshot, long start) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> save(planId, job, solved, geometry, result, snapshot, start));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_SAVE_ATTEMPTS) {
                    throw e;
                }
                log.debug("Plan {} changed while saving a re-plan, retrying ({})", planId, attempt);
            }
        }
    }

    private Saved save(long planId, Job job, Map<Long, List<Long>> solved, Map<Long, RouteGeometry> geometry,
                       OptimizationResult result, Snapshot snapshot, long start) {
        DispatchPlan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        PlanState state = state(plan);
        List<Long> vehicleIds = state.vehicles().stream().map(Vehicle::getId).toList();
        RouteReconciler.Result merged = reconciler.reconcile(solved, state.stops(), vehicleIds);

        // Recompute times from the merged routes so ETAs reflect the latest driver events.
        PlanSnapshot finalProblem = snapshotMapper.map(state, merged.orders());
        RoutingSolution evaluated = optimizer.evaluate(problemFactory.create(finalProblem.vehicles(), finalProblem.visits()));
        Map<String, Visit> visits = evaluated.getVisits().stream().collect(Collectors.toMap(Visit::getId, Function.identity()));
        Map<Long, Stop> stopsById = state.stops().stream().collect(Collectors.toMap(Stop::getId, Function.identity()));
        Map<Long, Route> routesByVehicle = routes.findByPlanIdOrderByVehicleId(planId).stream()
                .collect(Collectors.toMap(Route::getVehicleId, Function.identity()));

        int changed = 0;
        for (VehicleRoute vehicleRoute : evaluated.getVehicles()) {
            Long vehicleId = Long.valueOf(vehicleRoute.getId());
            List<Long> order = merged.orders().get(vehicleId);
            boolean orderChanged = !order.equals(snapshot.previousOrder().getOrDefault(vehicleId, List.of()));
            Route route = routesByVehicle.computeIfAbsent(vehicleId, id -> new Route(plan, id));
            RouteGeometry roads = null;
            if (orderChanged || route.getId() == null) {
                changed++;
                roads = order.equals(solved.get(vehicleId)) && geometry.containsKey(vehicleId)
                        ? geometry.get(vehicleId)
                        : roadGeometry(state.depot(), order, finalProblem);
            }
            for (int i = 0; i < order.size(); i++) {
                Stop stop = stopsById.get(order.get(i));
                if (!stop.isPending()) {
                    // Visited or in progress: keep the planned times and leg as history; only the position moves.
                    stop.resequence(i);
                    continue;
                }
                Visit visit = visits.get(stop.getId().toString());
                LocalTime arrival = time(visit.getArrivalTime());
                LocalTime departure = time(visit.departureTime());
                if (roads != null) {
                    stop.assign(vehicleId, i, arrival, departure, roads.legs().get(i).path());
                } else {
                    stop.reschedule(i, arrival, departure);
                }
                if (plan.isLive()) {
                    stop.commitEta();
                }
            }
            if (roads != null) {
                route.updateGeometry(roads.fullPath(), roads.legs().isEmpty() ? List.of() : roads.legs().getLast().path());
            }
            // A van that has already left keeps its real departure; only vans still at the depot get a new one.
            boolean departed = route.getId() != null && order.stream().anyMatch(id -> !stopsById.get(id).isPending());
            LocalTime departDepot = departed ? route.getDepartDepot() : time(vehicleRoute.departureTime());
            route.updateTotals(vehicleRoute.totalMeters(), vehicleRoute.totalTravelSeconds(), departDepot,
                    time(vehicleRoute.returnTime()), vehicleRoute.totalDemand());
            routes.save(route);
        }
        for (Long stopId : merged.unassigned()) {
            stopsById.get(stopId).unassign();
        }

        long travelSeconds = evaluated.getVehicles().stream().mapToLong(VehicleRoute::totalTravelSeconds).sum();
        long meters = evaluated.getVehicles().stream().mapToLong(VehicleRoute::totalMeters).sum();
        String score = evaluated.getScore().toString();
        int version = plan.recordSolution(score, travelSeconds, meters);
        revisions.save(new PlanRevision(planId, version, job.reason(), job.detail(), result.elapsedMillis(),
                (System.nanoTime() - start) / 1_000_000, score, travelSeconds, meters, finalProblem.pinnedStops(), changed));
        return new Saved(version, changed, score);
    }

    private RouteGeometry roadGeometry(GeoPoint depot, List<Long> order, PlanSnapshot problem) {
        if (order.isEmpty()) {
            return new RouteGeometry(List.of());
        }
        Map<String, GeoPoint> points = new LinkedHashMap<>();
        problem.visits().forEach(v -> points.put(v.id(), v.point()));
        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(depot);
        order.forEach(id -> waypoints.add(points.get(id.toString())));
        waypoints.add(depot);
        return matrixService.route(waypoints);
    }

    private static Map<Long, List<Long>> orders(RoutingSolution solution) {
        Map<Long, List<Long>> orders = new LinkedHashMap<>();
        for (VehicleRoute route : solution.getVehicles()) {
            orders.put(Long.valueOf(route.getId()), route.getVisits().stream().map(v -> Long.valueOf(v.getId())).toList());
        }
        return orders;
    }

    private static LocalTime time(Long secondsOfDay) {
        return LocalTime.ofSecondOfDay(Math.min(LocalTime.MAX.toSecondOfDay(), secondsOfDay));
    }

    private static LocalTime time(long secondsOfDay) {
        return time(Long.valueOf(secondsOfDay));
    }
}
