package com.routedispatch.plan;

import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.VisitSpec;
import java.util.List;

/** Solver input derived from a {@link PlanState}; {@code pinnedStops} counts visits the solver may not move. */
public record PlanSnapshot(List<VehicleSpec> vehicles, List<VisitSpec> visits, int pinnedStops) {
}
