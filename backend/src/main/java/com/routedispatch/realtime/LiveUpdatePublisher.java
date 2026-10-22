package com.routedispatch.realtime;

import com.routedispatch.plan.PlanEventPublisher;
import com.routedispatch.plan.PlanEvents;
import com.routedispatch.tracking.PositionPublisher;
import com.routedispatch.tracking.TrackingDtos;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes plan changes to dispatchers. Topics per plan:
 * {@code /topic/plans/{id}/updates} (typed envelope: SOLVE_PROGRESS, PLAN_UPDATED, PLAN_FAILED),
 * {@code /topic/plans/{id}/stops} and {@code /topic/plans/{id}/positions}.
 */
@Component
public class LiveUpdatePublisher implements PlanEventPublisher, PositionPublisher {

    /** Message shape on the updates topic so one subscription can carry several event kinds. */
    public record Envelope(String type, Object payload) {
    }

    private final SimpMessagingTemplate messaging;

    public LiveUpdatePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void solveProgress(PlanEvents.SolveProgress progress) {
        messaging.convertAndSend(updates(progress.planId()), new Envelope("SOLVE_PROGRESS", progress));
    }

    @Override
    public void planUpdated(PlanEvents.PlanUpdated update) {
        messaging.convertAndSend(updates(update.planId()), new Envelope("PLAN_UPDATED", update));
    }

    @Override
    public void planFailed(PlanEvents.PlanFailed failure) {
        messaging.convertAndSend(updates(failure.planId()), new Envelope("PLAN_FAILED", failure));
    }

    @Override
    public void stopUpdated(PlanEvents.StopUpdated update) {
        messaging.convertAndSend("/topic/plans/" + update.planId() + "/stops", update);
    }

    @Override
    public void positionUpdated(long planId, TrackingDtos.VehiclePositionView position) {
        messaging.convertAndSend("/topic/plans/" + planId + "/positions", position);
    }

    private static String updates(long planId) {
        return "/topic/plans/" + planId + "/updates";
    }
}
