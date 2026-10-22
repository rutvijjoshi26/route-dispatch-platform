package com.routedispatch.optimization;

import static com.routedispatch.optimization.TestRoutes.H;
import static com.routedispatch.optimization.TestRoutes.M;
import static com.routedispatch.optimization.TestRoutes.connect;
import static com.routedispatch.optimization.TestRoutes.vehicle;
import static com.routedispatch.optimization.TestRoutes.visit;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingConstraintProviderTest {

    final ConstraintVerifier<RoutingConstraintProvider, RoutingSolution> verifier =
            ConstraintVerifier.build(new RoutingConstraintProvider(), RoutingSolution.class, VehicleRoute.class, Visit.class);
    final List<Location> loc = TestRoutes.locations();

    @Test
    void capacityWithinLimitIsNotPenalized() {
        VehicleRoute van = vehicle("1", loc.get(0), 5, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 2, 8 * H, 12 * H);
        Visit b = visit("b", loc.get(2), 3, 8 * H, 12 * H);
        connect(van, a, b);

        verifier.verifyThat(RoutingConstraintProvider::vehicleCapacity).given(van, a, b).penalizesBy(0);
    }

    @Test
    void capacityOverflowIsPenalizedPerUnitWithWeight() {
        VehicleRoute van = vehicle("1", loc.get(0), 4, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 3, 8 * H, 12 * H);
        Visit b = visit("b", loc.get(2), 3, 8 * H, 12 * H);
        connect(van, a, b);

        verifier.verifyThat(RoutingConstraintProvider::vehicleCapacity)
                .given(van, a, b)
                .penalizesBy(2 * RoutingConstraintProvider.CAPACITY_UNIT_WEIGHT);
    }

    @Test
    void arrivalAfterWindowEndIsPenalizedBySecondsLate() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 8 * H + 5 * M);
        connect(van, a);

        // arrives 08:10, window closed 08:05
        verifier.verifyThat(RoutingConstraintProvider::timeWindowEnd).given(van, a).penalizesBy(5 * M);
    }

    @Test
    void arrivalInsideWindowIsNotPenalized() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 9 * H);
        connect(van, a);

        verifier.verifyThat(RoutingConstraintProvider::timeWindowEnd).given(van, a).penalizesBy(0);
    }

    @Test
    void lateArrivalThatAlreadyHappenedIsNotPenalized() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit done = visit("done", loc.get(1), 1, 8 * H, 9 * H);
        done.fix(10 * H, 10 * H + 5 * M);
        connect(van, done);

        verifier.verifyThat(RoutingConstraintProvider::timeWindowEnd).given(van, done).penalizesBy(0);
    }

    @Test
    void returningAfterShiftEndIsPenalizedBySecondsOver() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 8 * H + 20 * M);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        connect(van, a);

        // depart 08:00, arrive 08:10, leave 08:15, back 08:25 -> 5 minutes over
        verifier.verifyThat(RoutingConstraintProvider::shiftEnd).given(van, a).penalizesBy(5 * M);
    }

    @Test
    void unassignedVisitIsPenalizedOnMediumLevel() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit assigned = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        Visit left = visit("b", loc.get(2), 1, 8 * H, 12 * H);
        connect(van, assigned);

        verifier.verifyThat(RoutingConstraintProvider::unassignedVisit).given(van, assigned, left).penalizesBy(1);
    }

    @Test
    void travelTimeSumsAllLegsIncludingReturn() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        Visit b = visit("b", loc.get(2), 1, 8 * H, 12 * H);
        connect(van, a, b);

        verifier.verifyThat(RoutingConstraintProvider::travelTime).given(van, a, b).penalizesBy(600 + 300 + 900);
    }

    @Test
    void arrivalWithinGraceOfCommittedEtaIsNotPenalized() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        a.setCommittedEta(8 * H);
        connect(van, a);

        // arrives 08:10, promised 08:00, grace 15 minutes
        verifier.verifyThat(RoutingConstraintProvider::committedEtaLateness).given(van, a).penalizesBy(0);
    }

    @Test
    void arrivalBeyondGraceOfCommittedEtaIsPenalized() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        Visit b = visit("b", loc.get(2), 1, 8 * H, 12 * H);
        b.setCommittedEta(7 * H + 30 * M);
        connect(van, a, b);

        // b arrives 08:20, promised 07:30 + 15 min grace -> 35 minutes over
        verifier.verifyThat(RoutingConstraintProvider::committedEtaLateness).given(van, a, b).penalizesBy(35 * M);
    }

    @Test
    void visitsWithoutCommittedEtaAreIgnoredByEtaConstraint() {
        VehicleRoute van = vehicle("1", loc.get(0), 10, 8 * H, 17 * H);
        Visit a = visit("a", loc.get(1), 1, 8 * H, 12 * H);
        connect(van, a);

        verifier.verifyThat(RoutingConstraintProvider::committedEtaLateness).given(van, a).penalizesBy(0);
    }
}
