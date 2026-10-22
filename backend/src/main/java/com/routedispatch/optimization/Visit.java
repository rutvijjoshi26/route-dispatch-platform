package com.routedispatch.optimization;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;

/**
 * A delivery stop in the planning model. Times are seconds since midnight of the service day.
 *
 * <p>Visits the driver has already reached (or is driving to) carry fixed times taken from what really
 * happened; they sit in the pinned prefix of their vehicle's list and the solver never moves them.
 */
@PlanningEntity
public class Visit {

    @PlanningId
    private String id;
    private Location location;
    private int demand;
    private long windowStart;
    private long windowEnd;
    private long serviceSeconds;
    private Long fixedArrival;
    private Long fixedDeparture;
    private Long committedEta;

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    private VehicleRoute vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    private Visit previousVisit;

    @NextElementShadowVariable(sourceVariableName = "visits")
    private Visit nextVisit;

    @ShadowVariable(supplierName = "arrivalTimeSupplier")
    private Long arrivalTime;

    /** Required by Timefold for planning clones. */
    Visit() {
    }

    public Visit(String id, Location location, int demand, long windowStart, long windowEnd, long serviceSeconds) {
        this.id = id;
        this.location = location;
        this.demand = demand;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.serviceSeconds = serviceSeconds;
    }

    @ShadowSources({"vehicle", "previousVisit.arrivalTime"})
    public Long arrivalTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        if (fixedArrival != null) {
            return fixedArrival;
        }
        Long previousDeparture = previousVisit == null ? Long.valueOf(vehicle.departureTime()) : previousVisit.departureTime();
        if (previousDeparture == null) {
            return null;
        }
        Location from = previousVisit == null ? vehicle.getDepot() : previousVisit.getLocation();
        return Math.max(previousDeparture, vehicle.getAvailableFrom()) + from.secondsTo(location);
    }

    /** Marks this visit as already reached: its arrival and departure are facts, not plans. */
    public void fix(long arrival, long departure) {
        this.fixedArrival = arrival;
        this.fixedDeparture = departure;
    }

    public boolean isFixed() {
        return fixedArrival != null;
    }

    public Long serviceStartTime() {
        return arrivalTime == null ? null : Math.max(arrivalTime, windowStart);
    }

    public Long departureTime() {
        if (fixedDeparture != null) {
            return fixedDeparture;
        }
        return arrivalTime == null ? null : serviceStartTime() + serviceSeconds;
    }

    /** Driving time of the leg into this visit (from the depot or the previous visit). */
    public long travelSecondsFromPrevious() {
        if (vehicle == null) {
            return 0;
        }
        Location from = previousVisit == null ? vehicle.getDepot() : previousVisit.getLocation();
        return from.secondsTo(location);
    }

    /** Driving time back to the depot when this is the last visit of the route, otherwise zero. */
    public long travelSecondsToDepotIfLast() {
        return vehicle == null || nextVisit != null ? 0 : location.secondsTo(vehicle.getDepot());
    }

    public boolean isLastOfRoute() {
        return vehicle != null && nextVisit == null;
    }

    public long waitingSeconds() {
        return arrivalTime == null ? 0 : Math.max(0, windowStart - arrivalTime);
    }

    public long secondsLate() {
        return arrivalTime == null ? 0 : Math.max(0, arrivalTime - windowEnd);
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public int getDemand() {
        return demand;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public long getServiceSeconds() {
        return serviceSeconds;
    }

    public Long getCommittedEta() {
        return committedEta;
    }

    public void setCommittedEta(Long committedEta) {
        this.committedEta = committedEta;
    }

    public VehicleRoute getVehicle() {
        return vehicle;
    }

    public void setVehicle(VehicleRoute vehicle) {
        this.vehicle = vehicle;
    }

    public Visit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(Visit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Visit getNextVisit() {
        return nextVisit;
    }

    public void setNextVisit(Visit nextVisit) {
        this.nextVisit = nextVisit;
    }

    public Long getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Long arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    @Override
    public String toString() {
        return "Visit[" + id + "]";
    }
}
