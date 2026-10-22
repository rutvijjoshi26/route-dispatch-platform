package com.routedispatch.plan;

import com.routedispatch.common.ConflictException;
import com.routedispatch.fleet.Depot;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** One service day for one depot: its orders (stops), routes and lifecycle. */
@Entity
@Table(name = "dispatch_plan")
public class DispatchPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    /** Incremented on every solve or re-plan; drivers use it to detect route changes. */
    @Column(nullable = false)
    private int version;

    private String score;
    private Long travelSeconds;
    private Long distanceMeters;
    private Instant dispatchedAt;
    private LocalTime clockOrigin;
    private Double timeScale;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long rowVersion;

    protected DispatchPlan() {
    }

    public DispatchPlan(String name, LocalDate serviceDate, Depot depot) {
        this.name = name;
        this.serviceDate = serviceDate;
        this.depot = depot;
    }

    public void startPlanning() {
        if (status != PlanStatus.DRAFT && status != PlanStatus.PLANNED) {
            throw new ConflictException("Plan " + id + " cannot be optimized while " + status);
        }
        status = PlanStatus.PLANNING;
    }

    /** Records a finished solve or re-plan and returns the new version number. */
    public int recordSolution(String score, long travelSeconds, long distanceMeters) {
        if (status == PlanStatus.PLANNING) {
            status = PlanStatus.PLANNED;
        }
        this.score = score;
        this.travelSeconds = travelSeconds;
        this.distanceMeters = distanceMeters;
        return ++version;
    }

    public void planningFailed() {
        if (status == PlanStatus.PLANNING) {
            status = version == 0 ? PlanStatus.DRAFT : PlanStatus.PLANNED;
        }
    }

    public void dispatch(Instant now, LocalTime clockOrigin, double timeScale) {
        if (status != PlanStatus.PLANNED) {
            throw new ConflictException("Only a planned day can be dispatched; plan " + id + " is " + status);
        }
        if (timeScale < 1 || timeScale > 600) {
            throw new IllegalArgumentException("timeScale must be between 1 and 600");
        }
        this.status = PlanStatus.DISPATCHED;
        this.dispatchedAt = now;
        this.clockOrigin = clockOrigin;
        this.timeScale = timeScale;
    }

    public void complete() {
        if (status == PlanStatus.DISPATCHED) {
            status = PlanStatus.COMPLETED;
        }
    }

    public boolean isLive() {
        return status == PlanStatus.DISPATCHED;
    }

    public boolean acceptsOrders() {
        return status == PlanStatus.DRAFT || status == PlanStatus.PLANNED || status == PlanStatus.DISPATCHED;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public Depot getDepot() {
        return depot;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public String getScore() {
        return score;
    }

    public Long getTravelSeconds() {
        return travelSeconds;
    }

    public Long getDistanceMeters() {
        return distanceMeters;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public LocalTime getClockOrigin() {
        return clockOrigin;
    }

    public Double getTimeScale() {
        return timeScale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
