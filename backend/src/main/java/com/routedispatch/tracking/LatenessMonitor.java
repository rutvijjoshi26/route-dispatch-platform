package com.routedispatch.tracking;

import com.routedispatch.common.ConflictException;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.PlanClock;
import com.routedispatch.plan.PlanRevision.Reason;
import com.routedispatch.plan.PlanService;
import com.routedispatch.plan.PlanStatus;
import com.routedispatch.plan.Stop;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.plan.StopStatus;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects drivers running late and asks for a re-plan of the rest of their day. Two signals: a stop reached later
 * than its ETA, and a stop being driven to whose ETA has passed without an arrival (stuck in traffic). A van that
 * just triggered a re-plan is left alone for a cooldown period so one slow driver cannot cause a re-plan storm.
 */
@Component
public class LatenessMonitor {

    private static final Logger log = LoggerFactory.getLogger(LatenessMonitor.class);

    private final LatenessProperties properties;
    private final PlanService planService;
    private final DispatchPlanRepository plans;
    private final StopRepository stops;
    private final VehicleRepository vehicles;
    private final PlanClock clock;
    /** Last trigger per plan and van, in plan time. */
    private final Map<String, LocalTime> lastTrigger = new ConcurrentHashMap<>();

    public LatenessMonitor(LatenessProperties properties, PlanService planService, DispatchPlanRepository plans,
                           StopRepository stops, VehicleRepository vehicles, PlanClock clock) {
        this.properties = properties;
        this.planService = planService;
        this.plans = plans;
        this.stops = stops;
        this.vehicles = vehicles;
        this.clock = clock;
    }

    /** Returns true when the delay triggered a re-plan. */
    public boolean reportDelay(long planId, long vehicleId, Duration delay, LocalTime planNow) {
        if (delay.compareTo(properties.threshold()) <= 0) {
            return false;
        }
        String key = planId + ":" + vehicleId;
        LocalTime previous = lastTrigger.get(key);
        if (previous != null && !planNow.isAfter(previous.plus(properties.vehicleCooldown()))
                && !planNow.isBefore(previous)) {
            return false;
        }
        String vehicle = vehicles.findById(vehicleId).map(v -> v.getName()).orElse("Vehicle " + vehicleId);
        String detail = "%s running %d min late at %s".formatted(vehicle, delay.toMinutes(), planNow.withNano(0));
        try {
            planService.requestReplan(planId, Reason.DRIVER_LATE, detail);
        } catch (ConflictException e) {
            // The day finished or is no longer re-plannable; a late last stop is nothing to act on.
            log.debug("Plan {}: late-driver re-plan skipped: {}", planId, e.getMessage());
            return false;
        }
        lastTrigger.put(key, planNow);
        log.info("Plan {}: {}", planId, detail);
        return true;
    }

    @Scheduled(fixedDelayString = "${dispatch.lateness.check-interval}", initialDelayString = "${dispatch.lateness.check-interval}")
    @Transactional(readOnly = true)
    public void scanInProgressStops() {
        for (DispatchPlan plan : plans.findByStatus(PlanStatus.DISPATCHED)) {
            LocalTime now = clock.now(plan);
            for (Stop stop : stops.findByPlanIdAndStatus(plan.getId(), StopStatus.EN_ROUTE)) {
                if (stop.getPlannedArrival() != null && stop.getVehicleId() != null) {
                    Duration overdue = Duration.ofSeconds(now.toSecondOfDay() - stop.getPlannedArrival().toSecondOfDay());
                    reportDelay(plan.getId(), stop.getVehicleId(), overdue, now);
                }
            }
        }
    }
}
