package com.routedispatch.plan;

import com.routedispatch.common.ConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/**
 * Service-day time of a dispatched plan. A plan can run accelerated (time scale above 1) so a whole day can be
 * demonstrated in minutes: plan time = clock origin + (instant - dispatched at) x time scale.
 */
@Component
public class PlanClock {

    private static final long END_OF_DAY = LocalTime.MAX.toSecondOfDay();

    private final Clock clock;

    public PlanClock(Clock clock) {
        this.clock = clock;
    }

    public LocalTime now(DispatchPlan plan) {
        return at(plan, clock.instant());
    }

    public LocalTime at(DispatchPlan plan, Instant instant) {
        if (plan.getDispatchedAt() == null) {
            throw new ConflictException("Plan " + plan.getId() + " has not been dispatched");
        }
        long realMillis = Math.max(0, Duration.between(plan.getDispatchedAt(), instant).toMillis());
        long planSeconds = plan.getClockOrigin().toSecondOfDay() + Math.round(realMillis * plan.getTimeScale() / 1000.0);
        return LocalTime.ofSecondOfDay(Math.min(END_OF_DAY, planSeconds));
    }

    public Instant instant() {
        return clock.instant();
    }
}
