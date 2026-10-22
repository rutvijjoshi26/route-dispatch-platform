package com.routedispatch.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit record of one solve or re-plan: why it happened, how long it took, and what it changed. */
@Entity
@Table(name = "plan_revision")
public class PlanRevision {

    public enum Reason { INITIAL, REOPTIMIZE, NEW_ORDER, DRIVER_LATE, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private long solveMillis;
    private long totalMillis;

    @Column(nullable = false)
    private String score;

    private long travelSeconds;
    private long distanceMeters;
    private int pinnedStops;
    private int changedVehicles;

    protected PlanRevision() {
    }

    public PlanRevision(Long planId, int version, Reason reason, String detail, long solveMillis, long totalMillis,
                        String score, long travelSeconds, long distanceMeters, int pinnedStops, int changedVehicles) {
        this.totalMillis = totalMillis;
        this.planId = planId;
        this.version = version;
        this.reason = reason;
        this.detail = detail;
        this.solveMillis = solveMillis;
        this.score = score;
        this.travelSeconds = travelSeconds;
        this.distanceMeters = distanceMeters;
        this.pinnedStops = pinnedStops;
        this.changedVehicles = changedVehicles;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public int getVersion() {
        return version;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getSolveMillis() {
        return solveMillis;
    }

    /** End to end: snapshot, travel matrix, solve, road geometry and saving. */
    public long getTotalMillis() {
        return totalMillis;
    }

    public String getScore() {
        return score;
    }

    public long getTravelSeconds() {
        return travelSeconds;
    }

    public long getDistanceMeters() {
        return distanceMeters;
    }

    public int getPinnedStops() {
        return pinnedStops;
    }

    public int getChangedVehicles() {
        return changedVehicles;
    }
}
