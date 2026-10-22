package com.routedispatch.tracking;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.routedispatch.common.ConflictException;
import com.routedispatch.plan.DispatchPlanRepository;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Finds the dispatched plan a van is currently driving. Pings arrive every few seconds per van, so positive
 * answers are cached briefly; "no plan" is never cached so a fresh dispatch is picked up immediately.
 */
@Component
public class ActivePlanResolver {

    private final DispatchPlanRepository plans;
    private final Cache<Long, Long> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5)).build();

    public ActivePlanResolver(DispatchPlanRepository plans) {
        this.plans = plans;
    }

    public long activePlanFor(long vehicleId) {
        Long cached = cache.getIfPresent(vehicleId);
        if (cached != null) {
            return cached;
        }
        Long planId = plans.findActivePlanIdForVehicle(vehicleId).stream().findFirst()
                .orElseThrow(() -> new ConflictException("Vehicle " + vehicleId + " has no dispatched plan"));
        cache.put(vehicleId, planId);
        return planId;
    }
}
