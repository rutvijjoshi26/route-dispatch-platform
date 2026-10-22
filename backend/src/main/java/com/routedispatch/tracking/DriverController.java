package com.routedispatch.tracking;

import com.routedispatch.security.CurrentUser;
import com.routedispatch.tracking.DriverRouteService.DriverRouteView;
import com.routedispatch.tracking.TrackingDtos.EventResult;
import com.routedispatch.tracking.TrackingDtos.IngestResult;
import com.routedispatch.tracking.TrackingDtos.PingBatch;
import com.routedispatch.tracking.TrackingDtos.StopEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver")
@Tag(name = "Driver", description = "Used by driver devices (and the simulator)")
public class DriverController {

    private final DriverRouteService routes;
    private final PositionIngestService positions;
    private final StopEventService events;

    public DriverController(DriverRouteService routes, PositionIngestService positions, StopEventService events) {
        this.routes = routes;
        this.positions = positions;
        this.events = events;
    }

    @GetMapping("/route")
    @Operation(summary = "Today's route; send If-None-Match with the last ETag to poll cheaply for re-plans")
    public ResponseEntity<DriverRouteView> route(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        CurrentUser driver = CurrentUser.from(jwt);
        String etag = "\"" + routes.currentVersionTag(driver) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(routes.route(driver));
    }

    @PostMapping("/positions")
    @Operation(summary = "Upload a batch of GPS pings (duplicates and out-of-order pings are handled)")
    public ResponseEntity<IngestResult> positions(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PingBatch batch) {
        return ResponseEntity.accepted().body(positions.ingest(CurrentUser.from(jwt), batch.pings()));
    }

    @PostMapping("/stops/{stopId}/events")
    @Operation(summary = "Report EN_ROUTE, ARRIVED, DELIVERED or FAILED for a stop (idempotent by eventId)")
    public EventResult event(@AuthenticationPrincipal Jwt jwt, @PathVariable long stopId,
                             @Valid @RequestBody StopEventRequest request) {
        return events.record(CurrentUser.from(jwt), stopId, request);
    }
}
