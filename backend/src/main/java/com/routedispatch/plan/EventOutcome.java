package com.routedispatch.plan;

/** Result of applying a driver event. Duplicates and stale events never change state. */
public enum EventOutcome {
    APPLIED,
    DUPLICATE,
    IGNORED_STALE
}
