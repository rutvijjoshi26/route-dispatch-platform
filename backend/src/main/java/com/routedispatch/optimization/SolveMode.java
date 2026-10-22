package com.routedispatch.optimization;

/** INITIAL plans a whole day from scratch; REPLAN warm-starts from the current routes with a short time budget. */
public enum SolveMode {
    INITIAL,
    REPLAN
}
