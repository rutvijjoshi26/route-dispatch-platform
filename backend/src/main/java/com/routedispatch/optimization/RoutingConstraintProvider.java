package com.routedispatch.optimization;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Hard: what cannot happen (overloaded van, late delivery, van back after shift end).
 * Medium: every order should be on a route.
 * Soft: drive as little as possible and keep the ETAs customers were already given.
 */
public class RoutingConstraintProvider implements ConstraintProvider {

    /** One package unit over capacity weighs as much as one hour of lateness. */
    public static final long CAPACITY_UNIT_WEIGHT = 3600;
    /** Arriving up to 15 minutes after the communicated ETA is not counted as late. */
    public static final long ETA_GRACE_SECONDS = 15 * 60;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                vehicleCapacity(factory),
                timeWindowEnd(factory),
                shiftEnd(factory),
                unassignedVisit(factory),
                travelTime(factory),
                committedEtaLateness(factory)
        };
    }

    Constraint vehicleCapacity(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .groupBy(Visit::getVehicle, ConstraintCollectors.sum(Visit::getDemand))
                .filter((vehicle, demand) -> demand > vehicle.getCapacity())
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (vehicle, demand) -> (demand - vehicle.getCapacity()) * CAPACITY_UNIT_WEIGHT)
                .asConstraint("Vehicle capacity");
    }

    Constraint timeWindowEnd(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(visit -> !visit.isFixed() && visit.secondsLate() > 0)
                .penalize(HardMediumSoftScore.ONE_HARD, Visit::secondsLate)
                .asConstraint("Delivery time window");
    }

    Constraint shiftEnd(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(visit -> visit.isLastOfRoute() && visit.getVehicle().returnTime() > visit.getVehicle().getShiftEnd())
                .penalize(HardMediumSoftScore.ONE_HARD,
                        visit -> visit.getVehicle().returnTime() - visit.getVehicle().getShiftEnd())
                .asConstraint("Driver shift end");
    }

    Constraint unassignedVisit(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Visit.class)
                .filter(visit -> visit.getVehicle() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Unassigned order");
    }

    Constraint travelTime(ConstraintFactory factory) {
        // Scored per leg rather than per route so a move only re-scores the legs it touches.
        return factory.forEach(Visit.class)
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        visit -> visit.travelSecondsFromPrevious() + visit.travelSecondsToDepotIfLast())
                .asConstraint("Total driving time");
    }

    Constraint committedEtaLateness(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(visit -> visit.getCommittedEta() != null && !visit.isFixed() && etaOverrun(visit) > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, RoutingConstraintProvider::etaOverrun)
                .asConstraint("Committed ETA lateness");
    }

    private static long etaOverrun(Visit visit) {
        Long arrival = visit.getArrivalTime();
        return arrival == null ? 0 : Math.max(0, arrival - visit.getCommittedEta() - ETA_GRACE_SECONDS);
    }
}
