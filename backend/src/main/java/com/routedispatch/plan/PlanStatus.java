package com.routedispatch.plan;

/** DRAFT (collecting orders) -> PLANNING (solver running) -> PLANNED -> DISPATCHED (drivers on the road) -> COMPLETED. */
public enum PlanStatus {
    DRAFT,
    PLANNING,
    PLANNED,
    DISPATCHED,
    COMPLETED
}
