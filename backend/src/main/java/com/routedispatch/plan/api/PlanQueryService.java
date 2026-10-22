package com.routedispatch.plan.api;

import com.routedispatch.common.NotFoundException;
import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.PlanClock;
import com.routedispatch.plan.PlanRevisionRepository;
import com.routedispatch.plan.ReplanCoordinator;
import com.routedispatch.plan.Route;
import com.routedispatch.plan.RouteRepository;
import com.routedispatch.plan.Stop;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.plan.StopStatus;
import com.routedispatch.plan.api.PlanViews.ClockView;
import com.routedispatch.plan.api.PlanViews.DepotView;
import com.routedispatch.plan.api.PlanViews.PlanSummaryView;
import com.routedispatch.plan.api.PlanViews.PlanView;
import com.routedispatch.plan.api.PlanViews.RevisionView;
import com.routedispatch.plan.api.PlanViews.RouteView;
import com.routedispatch.plan.api.PlanViews.StopView;
import com.routedispatch.plan.api.PlanViews.TotalsView;
import com.routedispatch.routing.GeoPoint;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the read models the dispatcher UI and drivers consume. */
@Service
@Transactional(readOnly = true)
public class PlanQueryService {

    private final DispatchPlanRepository plans;
    private final StopRepository stops;
    private final RouteRepository routes;
    private final VehicleRepository vehicles;
    private final PlanRevisionRepository revisions;
    private final ReplanCoordinator coordinator;
    private final PlanClock clock;

    public PlanQueryService(DispatchPlanRepository plans, StopRepository stops, RouteRepository routes,
                            VehicleRepository vehicles, PlanRevisionRepository revisions, ReplanCoordinator coordinator,
                            PlanClock clock) {
        this.plans = plans;
        this.stops = stops;
        this.routes = routes;
        this.vehicles = vehicles;
        this.revisions = revisions;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public List<PlanSummaryView> list() {
        return plans.findAllByOrderByServiceDateDescIdDesc().stream()
                .map(p -> new PlanSummaryView(p.getId(), p.getName(), p.getServiceDate(), p.getStatus(), p.getVersion(),
                        stops.countByPlanId(p.getId()), p.getDepot().getName(), p.getCreatedAt()))
                .toList();
    }

    public PlanView get(long planId) {
        DispatchPlan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        List<Stop> all = stops.findByPlanIdOrderById(planId);
        Map<Long, Route> routeByVehicle = routes.findByPlanIdOrderByVehicleId(planId).stream()
                .collect(Collectors.toMap(Route::getVehicleId, Function.identity()));
        Map<Long, List<Stop>> stopsByVehicle = all.stream().filter(s -> s.getVehicleId() != null)
                .sorted(Comparator.comparing(Stop::getSequence))
                .collect(Collectors.groupingBy(Stop::getVehicleId));

        List<RouteView> routeViews = vehicles.findByDepotIdOrderById(plan.getDepot().getId()).stream()
                .map(v -> route(v, routeByVehicle.get(v.getId()), stopsByVehicle.getOrDefault(v.getId(), List.of())))
                .toList();
        List<StopView> unassigned = all.stream().filter(s -> s.getVehicleId() == null).map(PlanQueryService::stop).toList();
        int late = (int) all.stream().filter(PlanQueryService::isLate).count();
        int delivered = (int) all.stream().filter(s -> s.getStatus() == StopStatus.DELIVERED).count();
        int failed = (int) all.stream().filter(s -> s.getStatus() == StopStatus.FAILED).count();
        TotalsView totals = new TotalsView(all.size(), all.size() - unassigned.size(), unassigned.size(), delivered,
                failed, late, orZero(plan.getDistanceMeters()), orZero(plan.getTravelSeconds()));
        ClockView clockView = plan.isLive()
                ? new ClockView(clock.now(plan), plan.getTimeScale(), plan.getDispatchedAt()) : null;
        Depot depot = plan.getDepot();
        return new PlanView(plan.getId(), plan.getName(), plan.getServiceDate(), plan.getStatus(), plan.getVersion(),
                plan.getScore(), coordinator.isBusy(planId),
                new DepotView(depot.getId(), depot.getName(), depot.getAddress(), depot.getLocation().lat(),
                        depot.getLocation().lon()),
                clockView, totals, routeViews, unassigned);
    }

    public List<RevisionView> revisions(long planId) {
        if (!plans.existsById(planId)) {
            throw new NotFoundException("Plan", planId);
        }
        return revisions.findByPlanIdOrderByVersionDesc(planId).stream()
                .map(r -> new RevisionView(r.getVersion(), r.getReason().name(), r.getDetail(), r.getCreatedAt(),
                        r.getSolveMillis(), r.getTotalMillis(), r.getScore(), r.getTravelSeconds(), r.getDistanceMeters(),
                        r.getPinnedStops(), r.getChangedVehicles()))
                .toList();
    }

    private static RouteView route(Vehicle vehicle, Route route, List<Stop> routeStops) {
        List<StopView> stopViews = routeStops.stream().map(PlanQueryService::stop).toList();
        if (route == null) {
            return new RouteView(vehicle.getId(), vehicle.getName(), vehicle.getCapacity(), vehicle.getShiftStart(),
                    vehicle.getShiftEnd(), 0, 0, 0, null, null, List.of(), stopViews);
        }
        return new RouteView(vehicle.getId(), vehicle.getName(), vehicle.getCapacity(), vehicle.getShiftStart(),
                vehicle.getShiftEnd(), route.getLoadUnits(), route.getDistanceMeters(), route.getTravelSeconds(),
                route.getDepartDepot(), route.getReturnDepot(), coordinates(route.getPath()), stopViews);
    }

    static StopView stop(Stop s) {
        return new StopView(s.getId(), s.getOrderRef(), s.getCustomerName(), s.getAddress(), s.getLocation().lat(),
                s.getLocation().lon(), s.getWindowStart(), s.getWindowEnd(), s.getPackageSize(), s.getDemand(),
                s.getStatus(), s.getVehicleId(), s.getSequence(), s.getPlannedArrival(), s.getPlannedDeparture(),
                s.getCommittedEta(), s.getActualArrival(), s.getActualDeparture(), isLate(s), s.isAddedMidShift());
    }

    /** Late if the van actually arrived, or is planned to arrive, after the window closed. */
    static boolean isLate(Stop s) {
        LocalTime arrival = s.getActualArrival() != null ? s.getActualArrival() : s.getPlannedArrival();
        return arrival != null && arrival.isAfter(s.getWindowEnd());
    }

    static List<double[]> coordinates(List<GeoPoint> path) {
        return path.stream().map(p -> new double[] {p.lat(), p.lon()}).toList();
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
