package com.routedispatch.plan;

import com.routedispatch.plan.PlanRevision.Reason;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs at most one planning job per plan at a time. Requests that arrive while a job runs are merged into a
 * single follow-up job, so a burst of new orders or late-driver alerts costs one extra re-plan, not one each.
 * A pending full re-optimization is never downgraded to a quick re-plan by a later request.
 */
public class ReplanCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReplanCoordinator.class);

    @FunctionalInterface
    public interface JobRunner {
        void run(long planId, PlanningEngine.Job job);
    }

    private static final class Slot {
        PlanningEngine.Job pending;
    }

    private final JobRunner runner;
    private final Executor executor;
    /** A plan has a slot while a job for it is queued or running. Guarded by {@code this}. */
    private final Map<Long, Slot> slots = new HashMap<>();

    public ReplanCoordinator(JobRunner runner, Executor executor) {
        this.runner = runner;
        this.executor = executor;
    }

    public void request(long planId, PlanningEngine.Job job) {
        boolean start;
        synchronized (this) {
            Slot slot = slots.get(planId);
            start = slot == null;
            if (start) {
                slot = new Slot();
                slots.put(planId, slot);
            }
            slot.pending = merge(slot.pending, job);
        }
        if (start) {
            executor.execute(() -> drain(planId));
        }
    }

    public synchronized boolean isBusy(long planId) {
        return slots.containsKey(planId);
    }

    private void drain(long planId) {
        while (true) {
            PlanningEngine.Job job;
            synchronized (this) {
                Slot slot = slots.get(planId);
                job = slot.pending;
                slot.pending = null;
                if (job == null) {
                    slots.remove(planId);
                    return;
                }
            }
            try {
                runner.run(planId, job);
            } catch (RuntimeException e) {
                log.error("Planning job for plan {} failed", planId, e);
            }
        }
    }

    static PlanningEngine.Job merge(PlanningEngine.Job pending, PlanningEngine.Job incoming) {
        if (pending == null) {
            return incoming;
        }
        Reason reason = isFull(pending.reason()) && !isFull(incoming.reason()) ? pending.reason() : incoming.reason();
        String detail = pending.detail() == null ? incoming.detail()
                : incoming.detail() == null ? pending.detail() : pending.detail() + "; " + incoming.detail();
        return new PlanningEngine.Job(reason, detail);
    }

    private static boolean isFull(Reason reason) {
        return reason == Reason.INITIAL || reason == Reason.REOPTIMIZE;
    }
}
