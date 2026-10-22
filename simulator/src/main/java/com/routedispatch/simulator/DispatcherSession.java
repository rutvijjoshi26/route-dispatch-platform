package com.routedispatch.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dispatcher actions the simulator needs: find the demo day, optimize it and dispatch it. */
final class DispatcherSession {

    private static final Logger log = LoggerFactory.getLogger(DispatcherSession.class);

    private final ApiClient api;
    private final String token;

    DispatcherSession(ApiClient api, String email, String password) {
        this.api = api;
        this.token = api.login(email, password);
    }

    String token() {
        return token;
    }

    /** The plan with the given name, newest first (the demo profile creates "Chicago demo day"). */
    long findPlan(String name) {
        for (JsonNode plan : api.get(token, "/api/plans")) {
            if (plan.get("name").asText().equals(name)) {
                return plan.get("id").asLong();
            }
        }
        throw new IllegalStateException("No plan named '" + name + "'");
    }

    JsonNode plan(long planId) {
        return api.get(token, "/api/plans/" + planId);
    }

    /** Optimizes a draft day and dispatches it at {@code timeScale}; a dispatched day is left as it is. */
    void ensureDispatched(long planId, double timeScale) throws InterruptedException {
        JsonNode plan = plan(planId);
        if (plan.get("status").asText().equals("DRAFT")) {
            log.info("Optimizing plan {} ({} stops)...", planId, plan.at("/totals/stops").asInt());
            api.post(token, "/api/plans/" + planId + "/optimize", null);
            plan = awaitIdle(planId);
            log.info("Optimized: {} km, score {}", plan.at("/totals/distanceMeters").asLong() / 1000, plan.get("score").asText());
        }
        if (plan.get("status").asText().equals("PLANNED")) {
            api.post(token, "/api/plans/" + planId + "/dispatch", java.util.Map.of("timeScale", timeScale));
            log.info("Dispatched plan {} at {}x speed", planId, timeScale);
        } else if (!plan.get("status").asText().equals("DISPATCHED")) {
            throw new IllegalStateException("Plan " + planId + " is " + plan.get("status").asText());
        }
    }

    JsonNode awaitIdle(long planId) throws InterruptedException {
        while (true) {
            JsonNode plan = plan(planId);
            if (!plan.get("optimizing").asBoolean() && !plan.get("status").asText().equals("PLANNING")) {
                return plan;
            }
            Thread.sleep(500);
        }
    }
}
