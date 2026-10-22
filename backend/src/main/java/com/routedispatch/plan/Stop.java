package com.routedispatch.plan;

import com.routedispatch.common.Geometries;
import com.routedispatch.routing.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * A customer order to deliver, plus where it sits in the plan (vehicle, sequence, ETA) and what actually
 * happened (status and actual times). Times are on the plan's service day.
 */
@Entity
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private DispatchPlan plan;

    @Column(nullable = false)
    private String orderRef;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String address;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false)
    private LocalTime windowStart;

    @Column(nullable = false)
    private LocalTime windowEnd;

    private int serviceSeconds;
    private int demand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageSize packageSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopStatus status = StopStatus.PENDING;

    /** Reference to the assigned van by id (vehicles are a separate aggregate). */
    private Long vehicleId;
    private Integer sequence;
    private LocalTime plannedArrival;
    private LocalTime plannedDeparture;
    private LocalTime committedEta;
    private LocalTime enRouteAt;
    private LocalTime actualArrival;
    private LocalTime actualDeparture;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString inboundLeg;

    private boolean addedMidShift;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long rowVersion;

    protected Stop() {
    }

    public static Stop newOrder(DispatchPlan plan, String orderRef, String customerName, String address, GeoPoint location,
                                LocalTime windowStart, LocalTime windowEnd, PackageSize size, boolean addedMidShift) {
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("Delivery window must end after it starts");
        }
        Stop stop = new Stop();
        stop.plan = plan;
        stop.orderRef = orderRef;
        stop.customerName = customerName;
        stop.address = address;
        stop.location = Geometries.point(location);
        stop.windowStart = windowStart;
        stop.windowEnd = windowEnd;
        stop.packageSize = size;
        stop.demand = size.units();
        stop.serviceSeconds = size.serviceSeconds();
        stop.addedMidShift = addedMidShift;
        return stop;
    }

    /**
     * Applies a driver event. Status only moves forward, so duplicates and events that arrive after a later one
     * (out-of-order delivery over a flaky network) are ignored instead of corrupting the stop.
     */
    public EventOutcome apply(StopEventType type, LocalTime at) {
        StopStatus target = type.target();
        if (status.isTerminal() || target.rank() <= status.rank()) {
            return EventOutcome.IGNORED_STALE;
        }
        switch (type) {
            case EN_ROUTE -> enRouteAt = at;
            case ARRIVED -> actualArrival = at;
            case DELIVERED, FAILED -> {
                if (actualArrival == null) {
                    actualArrival = at;
                }
                actualDeparture = at;
            }
        }
        status = target;
        return EventOutcome.APPLIED;
    }

    public void assign(Long vehicleId, int sequence, LocalTime plannedArrival, LocalTime plannedDeparture,
                       List<GeoPoint> inboundPath) {
        this.vehicleId = vehicleId;
        this.sequence = sequence;
        this.plannedArrival = plannedArrival;
        this.plannedDeparture = plannedDeparture;
        this.inboundLeg = Geometries.lineString(inboundPath);
    }

    /** Updates position and ETA while keeping the stored road leg (used when a route's order did not change). */
    public void reschedule(int sequence, LocalTime plannedArrival, LocalTime plannedDeparture) {
        this.sequence = sequence;
        this.plannedArrival = plannedArrival;
        this.plannedDeparture = plannedDeparture;
    }

    /** Moves the stop to another position on its route without touching its planned times. */
    public void resequence(int sequence) {
        this.sequence = sequence;
    }

    public void unassign() {
        this.vehicleId = null;
        this.sequence = null;
        this.plannedArrival = null;
        this.plannedDeparture = null;
        this.inboundLeg = null;
    }

    /** Freezes the current ETA as the one communicated to the customer. */
    public void commitEta() {
        if (committedEta == null) {
            committedEta = plannedArrival;
        }
    }

    public boolean isPending() {
        return status == StopStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public DispatchPlan getPlan() {
        return plan;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getAddress() {
        return address;
    }

    public GeoPoint getLocation() {
        return Geometries.geoPoint(location);
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public int getServiceSeconds() {
        return serviceSeconds;
    }

    public int getDemand() {
        return demand;
    }

    public PackageSize getPackageSize() {
        return packageSize;
    }

    public StopStatus getStatus() {
        return status;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public LocalTime getPlannedArrival() {
        return plannedArrival;
    }

    public LocalTime getPlannedDeparture() {
        return plannedDeparture;
    }

    public LocalTime getCommittedEta() {
        return committedEta;
    }

    public LocalTime getEnRouteAt() {
        return enRouteAt;
    }

    public LocalTime getActualArrival() {
        return actualArrival;
    }

    public LocalTime getActualDeparture() {
        return actualDeparture;
    }

    public List<GeoPoint> getInboundLeg() {
        return Geometries.path(inboundLeg);
    }

    public boolean isAddedMidShift() {
        return addedMidShift;
    }
}
