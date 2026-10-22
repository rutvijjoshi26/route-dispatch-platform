package com.routedispatch.plan;

import com.routedispatch.common.Geometries;
import com.routedispatch.routing.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

/** Road geometry and totals of one van's route in a plan. Stops carry their own sequence and ETAs. */
@Entity
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private DispatchPlan plan;

    @Column(nullable = false)
    private Long vehicleId;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString geometry;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(LineString,4326)")
    private LineString returnLeg;

    private long distanceMeters;
    private long travelSeconds;

    @Column(nullable = false)
    private LocalTime departDepot;

    @Column(nullable = false)
    private LocalTime returnDepot;

    private int loadUnits;

    protected Route() {
    }

    public Route(DispatchPlan plan, Long vehicleId) {
        this.plan = plan;
        this.vehicleId = vehicleId;
    }

    /** Replaces the road geometry: the whole route and its final leg back to the depot. */
    public void updateGeometry(List<GeoPoint> path, List<GeoPoint> returnPath) {
        this.geometry = Geometries.lineString(path);
        this.returnLeg = Geometries.lineString(returnPath);
    }

    public void updateTotals(long distanceMeters, long travelSeconds, LocalTime departDepot, LocalTime returnDepot,
                             int loadUnits) {
        this.distanceMeters = distanceMeters;
        this.travelSeconds = travelSeconds;
        this.departDepot = departDepot;
        this.returnDepot = returnDepot;
        this.loadUnits = loadUnits;
    }

    public Long getId() {
        return id;
    }

    public DispatchPlan getPlan() {
        return plan;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public List<GeoPoint> getPath() {
        return Geometries.path(geometry);
    }

    public List<GeoPoint> getReturnLeg() {
        return Geometries.path(returnLeg);
    }

    public long getDistanceMeters() {
        return distanceMeters;
    }

    public long getTravelSeconds() {
        return travelSeconds;
    }

    public LocalTime getDepartDepot() {
        return departDepot;
    }

    public LocalTime getReturnDepot() {
        return returnDepot;
    }

    public int getLoadUnits() {
        return loadUnits;
    }
}
