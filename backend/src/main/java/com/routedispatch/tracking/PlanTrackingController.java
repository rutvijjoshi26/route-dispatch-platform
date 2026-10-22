package com.routedispatch.tracking;

import com.routedispatch.common.NotFoundException;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.tracking.TrackingDtos.NearbyVehicleView;
import com.routedispatch.tracking.TrackingDtos.TrailView;
import com.routedispatch.tracking.TrackingDtos.VehiclePositionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/plans/{planId}")
@Tag(name = "Live tracking", description = "Where the vans are")
public class PlanTrackingController {

    private final TrackingQueries queries;
    private final DispatchPlanRepository plans;

    public PlanTrackingController(TrackingQueries queries, DispatchPlanRepository plans) {
        this.queries = queries;
        this.plans = plans;
    }

    @GetMapping("/positions")
    @Operation(summary = "Latest known position of every van")
    public List<VehiclePositionView> positions(@PathVariable long planId) {
        plan(planId);
        return queries.latestPositions(planId);
    }

    @GetMapping("/vehicles/nearest")
    @Operation(summary = "Vans nearest to a point, e.g. a new order's address")
    public List<NearbyVehicleView> nearest(@PathVariable long planId,
                                           @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                                           @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
                                           @RequestParam(defaultValue = "3") @Min(1) @Max(20) int limit) {
        plan(planId);
        return queries.nearestVehicles(planId, lat, lon, limit);
    }

    @GetMapping("/vehicles/{vehicleId}/trail")
    @Operation(summary = "The path a van has driven, from its GPS pings")
    public TrailView trail(@PathVariable long planId, @PathVariable long vehicleId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        DispatchPlan plan = plan(planId);
        Instant from = since != null ? since : plan.getDispatchedAt() != null ? plan.getDispatchedAt() : Instant.EPOCH;
        return queries.trail(planId, vehicleId, from);
    }

    private DispatchPlan plan(long planId) {
        return plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
    }
}
