package com.routedispatch.tracking;

import com.routedispatch.common.NotFoundException;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.PlanClock;
import com.routedispatch.plan.Route;
import com.routedispatch.plan.RouteRepository;
import com.routedispatch.plan.Stop;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.plan.StopStatus;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.security.CurrentUser;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a driver's device needs: today's stops in order with the road leg to each, and the plan clock. */
@Service
@Transactional(readOnly = true)
public class DriverRouteService {

    public record ClockView(LocalTime now, LocalTime origin, double timeScale, Instant dispatchedAt) {
    }

    public record DriverStopView(long id, String orderRef, String customerName, String address, double lat, double lon,
                                 LocalTime windowStart, LocalTime windowEnd, int serviceSeconds, StopStatus status,
                                 int sequence, LocalTime plannedArrival, LocalTime plannedDeparture,
                                 List<double[]> inboundLeg) {
    }

    public record DriverRouteView(long planId, int planVersion, long vehicleId, String vehicleName, double depotLat,
                                  double depotLon, ClockView clock, LocalTime departDepot, List<DriverStopView> stops,
                                  List<double[]> returnLeg) {
    }

    private final ActivePlanResolver activePlans;
    private final DispatchPlanRepository plans;
    private final VehicleRepository vehicles;
    private final StopRepository stops;
    private final RouteRepository routes;
    private final PlanClock clock;

    public DriverRouteService(ActivePlanResolver activePlans, DispatchPlanRepository plans, VehicleRepository vehicles,
                              StopRepository stops, RouteRepository routes, PlanClock clock) {
        this.activePlans = activePlans;
        this.plans = plans;
        this.vehicles = vehicles;
        this.stops = stops;
        this.routes = routes;
        this.clock = clock;
    }

    /** Plan id and version of the driver's current plan, used as the HTTP ETag. */
    public String currentVersionTag(CurrentUser driver) {
        long planId = activePlans.activePlanFor(driver.vehicleId());
        int version = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId)).getVersion();
        return "p" + planId + "v" + version;
    }

    public DriverRouteView route(CurrentUser driver) {
        long vehicleId = driver.vehicleId();
        long planId = activePlans.activePlanFor(vehicleId);
        DispatchPlan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> new NotFoundException("Vehicle", vehicleId));
        Route route = routes.findByPlanIdAndVehicleId(planId, vehicleId).orElse(null);
        List<DriverStopView> stopViews = stops.findByPlanIdAndVehicleIdOrderBySequence(planId, vehicleId).stream()
                .map(DriverRouteService::stop).toList();
        GeoPoint depot = plan.getDepot().getLocation();
        return new DriverRouteView(planId, plan.getVersion(), vehicleId, vehicle.getName(), depot.lat(), depot.lon(),
                new ClockView(clock.now(plan), plan.getClockOrigin(), plan.getTimeScale(), plan.getDispatchedAt()),
                route == null ? vehicle.getShiftStart() : route.getDepartDepot(), stopViews,
                route == null ? List.of() : coordinates(route.getReturnLeg()));
    }

    private static DriverStopView stop(Stop s) {
        return new DriverStopView(s.getId(), s.getOrderRef(), s.getCustomerName(), s.getAddress(), s.getLocation().lat(),
                s.getLocation().lon(), s.getWindowStart(), s.getWindowEnd(), s.getServiceSeconds(), s.getStatus(),
                s.getSequence(), s.getPlannedArrival(), s.getPlannedDeparture(), coordinates(s.getInboundLeg()));
    }

    private static List<double[]> coordinates(List<GeoPoint> path) {
        return path.stream().map(p -> new double[] {p.lat(), p.lon()}).toList();
    }
}
