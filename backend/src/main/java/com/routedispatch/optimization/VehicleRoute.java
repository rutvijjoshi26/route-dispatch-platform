package com.routedispatch.optimization;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPinToIndex;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import java.util.ArrayList;
import java.util.List;

/**
 * One van and the ordered visits it will make. The first {@code pinnedCount} visits are already visited or
 * in progress, so the solver may only change what comes after them.
 */
@PlanningEntity
public class VehicleRoute {

    @PlanningId
    private String id;
    private String name;
    private int capacity;
    private Location depot;
    private long shiftStart;
    private long shiftEnd;
    private long availableFrom;

    @PlanningListVariable(allowsUnassignedValues = true)
    private List<Visit> visits = new ArrayList<>();

    @PlanningPinToIndex
    private int pinnedCount;

    /** Required by Timefold for planning clones. */
    VehicleRoute() {
    }

    public VehicleRoute(String id, String name, int capacity, Location depot, long shiftStart, long shiftEnd,
                        long availableFrom) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.depot = depot;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.availableFrom = availableFrom;
    }

    /** When the van leaves the depot: shift start, or later if the plan is made mid-day. */
    public long departureTime() {
        return Math.max(shiftStart, availableFrom);
    }

    public int totalDemand() {
        int total = 0;
        for (Visit visit : visits) {
            total += visit.getDemand();
        }
        return total;
    }

    public long totalTravelSeconds() {
        long total = 0;
        Location previous = depot;
        for (Visit visit : visits) {
            total += previous.secondsTo(visit.getLocation());
            previous = visit.getLocation();
        }
        return total + previous.secondsTo(depot);
    }

    public long totalMeters() {
        long total = 0;
        Location previous = depot;
        for (Visit visit : visits) {
            total += previous.metersTo(visit.getLocation());
            previous = visit.getLocation();
        }
        return total + previous.metersTo(depot);
    }

    /** Time back at the depot after the last visit. */
    public long returnTime() {
        if (visits.isEmpty()) {
            return departureTime();
        }
        Visit last = visits.getLast();
        Long departure = last.departureTime();
        long leaveLast = departure == null ? departureTime() : Math.max(departure, availableFrom);
        return leaveLast + last.getLocation().secondsTo(depot);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public Location getDepot() {
        return depot;
    }

    public long getShiftStart() {
        return shiftStart;
    }

    public long getShiftEnd() {
        return shiftEnd;
    }

    public long getAvailableFrom() {
        return availableFrom;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    public void setVisits(List<Visit> visits) {
        this.visits = visits;
    }

    public int getPinnedCount() {
        return pinnedCount;
    }

    public void setPinnedCount(int pinnedCount) {
        this.pinnedCount = pinnedCount;
    }

    @Override
    public String toString() {
        return "VehicleRoute[" + id + "]";
    }
}
