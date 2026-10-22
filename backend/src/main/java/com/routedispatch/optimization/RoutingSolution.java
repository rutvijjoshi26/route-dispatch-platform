package com.routedispatch.optimization;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import java.util.List;

@PlanningSolution
public class RoutingSolution {

    @PlanningEntityCollectionProperty
    private List<VehicleRoute> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Visit> visits;

    @PlanningScore
    private HardMediumSoftScore score;

    /** Required by Timefold for planning clones. */
    RoutingSolution() {
    }

    public RoutingSolution(List<VehicleRoute> vehicles, List<Visit> visits) {
        this.vehicles = vehicles;
        this.visits = visits;
    }

    public List<VehicleRoute> getVehicles() {
        return vehicles;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
