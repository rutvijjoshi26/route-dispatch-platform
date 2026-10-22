package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.fleet.Depot;
import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.routing.GeoPoint;
import com.routedispatch.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A solve interrupted by a restart must not leave the plan stuck in PLANNING. */
@IntegrationTest
class PlanningRecoveryIT {

    @Autowired DepotRepository depots;
    @Autowired DispatchPlanRepository plans;
    @Autowired PlanningRecovery recovery;

    @Test
    void plansLeftPlanningAfterARestartGoBackToTheirPreviousState() {
        Depot depot = depots.save(new Depot("Recovery depot", "1 N State St, Chicago, IL", new GeoPoint(41.88, -87.63)));
        DispatchPlan neverSolved = new DispatchPlan("Never solved", LocalDate.now(), depot);
        neverSolved.startPlanning();
        DispatchPlan solvedBefore = new DispatchPlan("Solved before", LocalDate.now(), depot);
        solvedBefore.startPlanning();
        solvedBefore.recordSolution("0hard/0medium/-1soft", 1, 1);
        solvedBefore.startPlanning();
        plans.save(neverSolved);
        plans.save(solvedBefore);

        recovery.resetInterruptedPlanning();

        assertThat(plans.findById(neverSolved.getId()).orElseThrow().getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plans.findById(solvedBefore.getId()).orElseThrow().getStatus()).isEqualTo(PlanStatus.PLANNED);
    }
}
