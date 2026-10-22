package com.routedispatch.plan;

/** Port for pushing plan changes to connected dispatchers (implemented over STOMP in the realtime package). */
public interface PlanEventPublisher {

    void solveProgress(PlanEvents.SolveProgress progress);

    void planUpdated(PlanEvents.PlanUpdated update);

    void planFailed(PlanEvents.PlanFailed failure);

    void stopUpdated(PlanEvents.StopUpdated update);
}
