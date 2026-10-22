package com.routedispatch.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solves run in memory, so a restart abandons any that were in flight. On startup, plans still marked PLANNING
 * return to DRAFT (never solved) or PLANNED (they keep their last saved routes) and can be optimized again.
 */
@Component
public class PlanningRecovery {

    private static final Logger log = LoggerFactory.getLogger(PlanningRecovery.class);

    private final DispatchPlanRepository plans;

    PlanningRecovery(DispatchPlanRepository plans) {
        this.plans = plans;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetInterruptedPlanning() {
        for (DispatchPlan plan : plans.findByStatus(PlanStatus.PLANNING)) {
            plan.planningFailed();
            log.warn("Plan {} was being optimized when the service stopped; it is now {}", plan.getId(), plan.getStatus());
        }
    }
}
