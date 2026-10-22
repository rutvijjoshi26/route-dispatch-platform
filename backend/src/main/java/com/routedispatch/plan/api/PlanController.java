package com.routedispatch.plan.api;

import com.routedispatch.plan.PlanRevision.Reason;
import com.routedispatch.plan.PlanService;
import com.routedispatch.plan.api.PlanRequests.CreatePlanRequest;
import com.routedispatch.plan.api.PlanRequests.DispatchRequest;
import com.routedispatch.plan.api.PlanRequests.NewOrderRequest;
import com.routedispatch.plan.api.PlanViews.PlanSummaryView;
import com.routedispatch.plan.api.PlanViews.PlanView;
import com.routedispatch.plan.api.PlanViews.RevisionView;
import com.routedispatch.plan.api.PlanViews.StopView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Plans", description = "Dispatcher operations on a delivery day")
public class PlanController {

    private final PlanService planService;
    private final PlanQueryService queries;

    public PlanController(PlanService planService, PlanQueryService queries) {
        this.planService = planService;
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "List delivery days, newest first")
    public List<PlanSummaryView> list() {
        return queries.list();
    }

    @PostMapping
    @Operation(summary = "Create an empty delivery day for a depot")
    public ResponseEntity<PlanView> create(@Valid @RequestBody CreatePlanRequest request) {
        long id = planService.createPlan(request.name().trim(), request.serviceDate(), request.depotId()).getId();
        return ResponseEntity.created(URI.create("/api/plans/" + id)).body(queries.get(id));
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Routes, stops, ETAs and totals of a delivery day")
    public PlanView get(@PathVariable long planId) {
        return queries.get(planId);
    }

    @PostMapping(path = "/{planId}/orders/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import orders from CSV (order_ref,customer,address,lat,lon,window_start,window_end,size)")
    public Map<String, Integer> importOrders(@PathVariable long planId, @RequestPart("file") MultipartFile file)
            throws IOException {
        return Map.of("imported", planService.importOrders(planId, file.getInputStream()));
    }

    @PostMapping("/{planId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add one order; on a planned or dispatched day this triggers a re-plan")
    public StopView addOrder(@PathVariable long planId, @Valid @RequestBody NewOrderRequest request) {
        return PlanQueryService.stop(planService.addOrder(planId, request.toDraft()));
    }

    @PostMapping("/{planId}/optimize")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Plan all routes (runs in the background; progress is pushed over WebSocket)")
    public void optimize(@PathVariable long planId) {
        planService.optimize(planId);
    }

    @PostMapping("/{planId}/dispatch")
    @Operation(summary = "Send the day to drivers and start the plan clock")
    public PlanView dispatch(@PathVariable long planId, @Valid @RequestBody DispatchRequest request) {
        planService.dispatch(planId, request.timeScale());
        return queries.get(planId);
    }

    @PostMapping("/{planId}/replan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Re-plan unvisited stops now, keeping completed and in-progress stops in place")
    public void replan(@PathVariable long planId) {
        planService.requestReplan(planId, Reason.MANUAL, "Requested by dispatcher");
    }

    @GetMapping("/{planId}/revisions")
    @Operation(summary = "History of solves and re-plans")
    public List<RevisionView> revisions(@PathVariable long planId) {
        return queries.revisions(planId);
    }
}
