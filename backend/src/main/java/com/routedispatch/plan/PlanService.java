package com.routedispatch.plan;

import com.routedispatch.common.ConflictException;
import com.routedispatch.common.NotFoundException;
import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.fleet.Vehicle;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.PlanRevision.Reason;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dispatcher commands on a plan. Solving is asynchronous and goes through {@link ReplanCoordinator}. */
@Service
public class PlanService {

    private final DispatchPlanRepository plans;
    private final StopRepository stops;
    private final DepotRepository depots;
    private final VehicleRepository vehicles;
    private final CsvOrderParser csvParser;
    private final ServiceArea serviceArea;
    private final ReplanCoordinator coordinator;
    private final PlanClock clock;
    private final PlanEventPublisher publisher;

    public PlanService(DispatchPlanRepository plans, StopRepository stops, DepotRepository depots,
                       VehicleRepository vehicles, CsvOrderParser csvParser, ServiceArea serviceArea,
                       ReplanCoordinator coordinator, PlanClock clock, PlanEventPublisher publisher) {
        this.plans = plans;
        this.stops = stops;
        this.depots = depots;
        this.vehicles = vehicles;
        this.csvParser = csvParser;
        this.serviceArea = serviceArea;
        this.coordinator = coordinator;
        this.clock = clock;
        this.publisher = publisher;
    }

    @Transactional
    public DispatchPlan createPlan(String name, LocalDate serviceDate, long depotId) {
        Depot depot = depots.findById(depotId).orElseThrow(() -> new NotFoundException("Depot", depotId));
        return plans.save(new DispatchPlan(name, serviceDate, depot));
    }

    /**
     * All-or-nothing import: any invalid row rejects the whole file. On an already planned day the new orders are
     * inserted by a re-plan. Returns the number of orders added.
     */
    @Transactional
    public int importOrders(long planId, InputStream csv) {
        DispatchPlan plan = plan(planId);
        if (plan.isLive() || plan.getStatus() == PlanStatus.PLANNING || plan.getStatus() == PlanStatus.COMPLETED) {
            throw new ConflictException("Orders can only be imported before the day is dispatched; add orders one by one instead");
        }
        CsvOrderParser.Result result = csvParser.parse(csv);
        List<CsvOrderParser.RowError> errors = new ArrayList<>(result.errors());
        for (int i = 0; i < result.orders().size(); i++) {
            String ref = result.orders().get(i).orderRef();
            if (stops.existsByPlanIdAndOrderRef(planId, ref)) {
                errors.add(new CsvOrderParser.RowError(i + 2, "order_ref " + ref + " already exists in this plan"));
            }
        }
        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
        List<Stop> created = result.orders().stream().map(draft -> newStop(plan, draft, false)).toList();
        stops.saveAll(created);
        if (plan.getVersion() > 0) {
            afterCommit(() -> coordinator.request(planId, new PlanningEngine.Job(Reason.NEW_ORDER,
                    created.size() + " orders imported")));
        }
        return created.size();
    }

    /**
     * Adds one order. On a planned or dispatched day this triggers a re-plan that inserts the order without
     * touching stops drivers have already reached.
     */
    @Transactional
    public Stop addOrder(long planId, OrderDraft draft) {
        DispatchPlan plan = plan(planId);
        if (!plan.acceptsOrders()) {
            throw new ConflictException("Plan " + planId + " does not accept orders while " + plan.getStatus());
        }
        if (!serviceArea.contains(draft.location())) {
            throw new IllegalArgumentException("The location is outside the service area");
        }
        if (stops.existsByPlanIdAndOrderRef(planId, draft.orderRef())) {
            throw new ConflictException("Order " + draft.orderRef() + " already exists in this plan");
        }
        if (plan.isLive() && !draft.windowEnd().isAfter(clock.now(plan))) {
            throw new IllegalArgumentException("The delivery window has already closed (plan time is " + clock.now(plan) + ")");
        }
        Stop stop = stops.save(newStop(plan, draft, plan.isLive()));
        if (plan.getVersion() > 0) {
            afterCommit(() -> coordinator.request(planId, new PlanningEngine.Job(Reason.NEW_ORDER,
                    "Order " + draft.orderRef() + " added")));
        }
        return stop;
    }

    @Transactional(readOnly = true)
    public void optimize(long planId) {
        DispatchPlan plan = plan(planId);
        if (plan.isLive()) {
            throw new ConflictException("Plan " + planId + " is dispatched; use re-plan to keep drivers' progress");
        }
        if (plan.getStatus() == PlanStatus.PLANNING || plan.getStatus() == PlanStatus.COMPLETED) {
            throw new ConflictException("Plan " + planId + " cannot be optimized while " + plan.getStatus());
        }
        Reason reason = plan.getVersion() == 0 ? Reason.INITIAL : Reason.REOPTIMIZE;
        coordinator.request(planId, new PlanningEngine.Job(reason, "Requested by dispatcher"));
    }

    @Transactional(readOnly = true)
    public void requestReplan(long planId, Reason reason, String detail) {
        DispatchPlan plan = plan(planId);
        if (!plan.isLive() && plan.getStatus() != PlanStatus.PLANNED) {
            throw new ConflictException("Only a planned or dispatched day can be re-planned; plan is " + plan.getStatus());
        }
        coordinator.request(planId, new PlanningEngine.Job(reason, detail));
    }

    /**
     * Sends the day to drivers. The plan clock starts at the earliest shift start and runs {@code timeScale}
     * times faster than real time. ETAs at this moment are what customers are told.
     */
    @Transactional
    public DispatchPlan dispatch(long planId, double timeScale) {
        DispatchPlan plan = plan(planId);
        if (coordinator.isBusy(planId)) {
            throw new ConflictException("Plan " + planId + " is still being optimized");
        }
        LocalTime origin = vehicles.findByDepotIdOrderById(plan.getDepot().getId()).stream()
                .map(Vehicle::getShiftStart).min(Comparator.naturalOrder()).orElse(LocalTime.of(8, 0));
        plan.dispatch(clock.instant(), origin, timeScale);
        stops.findByPlanIdOrderById(planId).stream().filter(s -> s.getVehicleId() != null).forEach(Stop::commitEta);
        afterCommit(() -> publisher.planUpdated(new PlanEvents.PlanUpdated(planId, plan.getVersion(), "DISPATCHED",
                "Dispatched at " + timeScale + "x speed", 0, 0, 0, 0, plan.getScore())));
        return plan;
    }

    private DispatchPlan plan(long planId) {
        return plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
    }

    private static Stop newStop(DispatchPlan plan, OrderDraft draft, boolean midShift) {
        return Stop.newOrder(plan, draft.orderRef(), draft.customerName(), draft.address(), draft.location(),
                draft.windowStart(), draft.windowEnd(), draft.size(), midShift);
    }

    /** Starts follow-up work only once the change is committed, so the planner reads it. */
    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
