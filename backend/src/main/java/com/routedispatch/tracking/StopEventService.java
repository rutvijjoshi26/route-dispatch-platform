package com.routedispatch.tracking;

import com.routedispatch.common.ConflictException;
import com.routedispatch.common.ForbiddenException;
import com.routedispatch.common.NotFoundException;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.EventOutcome;
import com.routedispatch.plan.PlanClock;
import com.routedispatch.plan.PlanEventPublisher;
import com.routedispatch.plan.PlanEvents;
import com.routedispatch.plan.Stop;
import com.routedispatch.plan.StopEventType;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.plan.StopStatus;
import com.routedispatch.security.CurrentUser;
import com.routedispatch.tracking.TrackingDtos.EventResult;
import com.routedispatch.tracking.TrackingDtos.StopEventRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies driver events to stops exactly once. The event id is the idempotency key: a retried event returns
 * the outcome recorded the first time, even when two copies race each other. Concurrent writes to the same stop
 * (for example a re-plan saving new ETAs) are resolved by optimistic locking and a retry.
 */
@Service
public class StopEventService {

    private static final int MAX_ATTEMPTS = 3;
    private static final Set<StopStatus> OPEN = EnumSet.of(StopStatus.PENDING, StopStatus.EN_ROUTE, StopStatus.ARRIVED);

    private record Applied(EventResult result, long planId, String orderRef, long vehicleId, LocalTime at,
                           Duration delay) {
    }

    private final StopRepository stops;
    private final DispatchPlanRepository plans;
    private final StopEventRepository events;
    private final PlanClock planClock;
    private final PlanEventPublisher publisher;
    private final LatenessMonitor latenessMonitor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public StopEventService(StopRepository stops, DispatchPlanRepository plans, StopEventRepository events, PlanClock planClock,
                            PlanEventPublisher publisher, LatenessMonitor latenessMonitor, TransactionTemplate tx,
                            Clock clock) {
        this.stops = stops;
        this.plans = plans;
        this.events = events;
        this.planClock = planClock;
        this.publisher = publisher;
        this.latenessMonitor = latenessMonitor;
        this.tx = tx;
        this.clock = clock;
    }

    public EventResult record(CurrentUser driver, long stopId, StopEventRequest request) {
        if (request.occurredAt().isAfter(clock.instant().plus(PositionIngestService.MAX_CLOCK_SKEW))) {
            throw new IllegalArgumentException("Event " + request.eventId() + " is timestamped in the future");
        }
        Applied applied = null;
        for (int attempt = 1; applied == null; attempt++) {
            try {
                applied = tx.execute(status -> apply(driver, stopId, request));
            } catch (DataIntegrityViolationException e) {
                // A concurrent copy of the same event won the insert; report what it recorded.
                if (!events.existsById(request.eventId())) {
                    throw e;
                }
                applied = tx.execute(status -> existing(stopId, request));
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
        if (applied.result().outcome() == EventOutcome.APPLIED && !applied.result().duplicate()) {
            publisher.stopUpdated(new PlanEvents.StopUpdated(applied.planId(), stopId, applied.orderRef(),
                    applied.vehicleId(), applied.result().status(), applied.at()));
            if (applied.delay() != null) {
                latenessMonitor.reportDelay(applied.planId(), applied.vehicleId(), applied.delay(), applied.at());
            }
        }
        return applied.result();
    }

    private Applied apply(CurrentUser driver, long stopId, StopEventRequest request) {
        if (events.existsById(request.eventId())) {
            return existing(stopId, request);
        }
        Stop stop = stops.findById(stopId).orElseThrow(() -> new NotFoundException("Stop", stopId));
        if (stop.getVehicleId() == null || !stop.getVehicleId().equals(driver.vehicleId())) {
            throw new ForbiddenException("Stop " + stopId + " is not on your route");
        }
        DispatchPlan plan = stop.getPlan();
        if (!plan.isLive()) {
            throw new ConflictException("Plan " + plan.getId() + " is not dispatched");
        }
        LocalTime at = planClock.at(plan, request.occurredAt());
        EventOutcome outcome = stop.apply(request.type(), at);
        events.saveAndFlush(new StopEvent(request.eventId(), stopId, driver.vehicleId(), request.type(),
                request.occurredAt(), outcome, request.note()));
        if (outcome == EventOutcome.APPLIED && stop.getStatus().isTerminal()) {
            completeIfDone(plan.getId());
        }
        Duration delay = null;
        if (outcome == EventOutcome.APPLIED && request.type() != StopEventType.EN_ROUTE && stop.getPlannedArrival() != null) {
            delay = Duration.ofSeconds(stop.getActualArrival().toSecondOfDay() - stop.getPlannedArrival().toSecondOfDay());
        }
        return new Applied(new EventResult(request.eventId(), stopId, outcome, stop.getStatus(), false), plan.getId(),
                stop.getOrderRef(), driver.vehicleId(), at, delay);
    }

    /**
     * Completes the day once every routed stop is finished. The plan row lock makes two drivers finishing their last
     * stops at the same moment take turns, so the second one sees the first one's committed stop.
     */
    private void completeIfDone(long planId) {
        stops.flush();
        DispatchPlan locked = plans.findByIdForUpdate(planId).orElseThrow();
        if (!stops.existsByPlanIdAndVehicleIdIsNotNullAndStatusIn(planId, OPEN)) {
            locked.complete();
        }
    }

    private Applied existing(long stopId, StopEventRequest request) {
        StopEvent event = events.findById(request.eventId()).orElseThrow();
        if (event.getStopId() != stopId) {
            throw new ConflictException("Event " + request.eventId() + " was already used for another stop");
        }
        Stop stop = stops.findById(stopId).orElseThrow(() -> new NotFoundException("Stop", stopId));
        return new Applied(new EventResult(event.getEventId(), stopId, event.getOutcome(), stop.getStatus(), true),
                stop.getPlan().getId(), stop.getOrderRef(), event.getVehicleId(), null, null);
    }
}
