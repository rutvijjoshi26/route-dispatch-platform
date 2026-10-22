package com.routedispatch.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API-level re-plan latency: as a dispatcher, add one order to the dispatched demo day and time until the
 * PLAN_UPDATED message for it arrives over STOMP (what the dispatcher's screen waits for).
 */
final class ReplanBenchMode {

    private static final Logger log = LoggerFactory.getLogger(ReplanBenchMode.class);

    static void run(Args args) throws Exception {
        ApiClient api = new ApiClient(args.string("api", "http://localhost:8080"));
        DispatcherSession dispatcher = new DispatcherSession(api, args.string("dispatcher", "dispatcher@demo.local"),
                args.string("dispatcher-password", "dispatch-demo"));
        long planId = dispatcher.findPlan(args.string("plan", "Chicago demo day"));
        dispatcher.ensureDispatched(planId, args.decimal("time-scale", 1));
        int runs = args.integer("runs", 10);
        Random random = new Random(args.integer("seed", 11));
        LinkedBlockingQueue<JsonNode> updates = new LinkedBlockingQueue<>();
        LatencyStats endToEnd = new LatencyStats();
        LatencyStats server = new LatencyStats();
        LatencyStats solver = new LatencyStats();
        List<Map<String, Object>> samples = new ArrayList<>();
        int assigned = 0;

        try (StompSubscriber subscriber = new StompSubscriber(api.baseUrl(), dispatcher.token(), (destination, body) -> {
            try {
                JsonNode envelope = ApiClient.JSON.readTree(body);
                if ("PLAN_UPDATED".equals(envelope.get("type").asText())) {
                    updates.add(envelope.get("payload"));
                }
            } catch (java.io.IOException e) {
                log.warn("Unreadable update: {}", body);
            }
        })) {
            subscriber.subscribe("/topic/plans/" + planId + "/updates");
            Thread.sleep(500);
            for (int run = 1; run <= runs; run++) {
                JsonNode before = dispatcher.awaitIdle(planId);
                updates.clear();
                LocalTime now = LocalTime.parse(before.at("/clock/now").asText());
                String ref = "BENCH-" + Instant.now().toEpochMilli();
                LocalTime windowStart = now.plusMinutes(30).withSecond(0).withNano(0);
                LocalTime latest = LocalTime.of(17, 0);
                LocalTime windowEnd = windowStart.isBefore(latest.minusHours(2)) ? latest : windowStart.plusHours(2);
                Map<String, Object> order = Map.of("orderRef", ref, "customerName", "Benchmark customer " + run,
                        "address", "Benchmark address, Chicago, IL",
                        "lat", 41.86 + random.nextDouble() * 0.10, "lon", -87.73 + random.nextDouble() * 0.08,
                        "windowStart", windowStart.toString(), "windowEnd", windowEnd.toString(), "size", "MEDIUM");
                long t0 = System.nanoTime();
                api.post(dispatcher.token(), "/api/plans/" + planId + "/orders", order);
                JsonNode update;
                do {
                    update = updates.poll(60, TimeUnit.SECONDS);
                    if (update == null) {
                        throw new IllegalStateException("No re-plan within 60 s for " + ref);
                    }
                } while (!update.get("detail").asText().contains(ref));
                long elapsed = System.nanoTime() - t0;
                endToEnd.record(elapsed);
                server.record(update.get("totalMillis").asLong() * 1_000_000);
                solver.record(update.get("solveMillis").asLong() * 1_000_000);
                boolean onRoute = isAssigned(dispatcher.plan(planId), ref);
                assigned += onRoute ? 1 : 0;
                samples.add(Map.of("run", run, "endToEndMillis", elapsed / 1_000_000, "serverMillis",
                        update.get("totalMillis").asLong(), "solverMillis", update.get("solveMillis").asLong(),
                        "pinnedStops", update.get("pinnedStops").asInt(), "changedVehicles",
                        update.get("changedVehicles").asInt(), "assigned", onRoute));
                log.info("Run {}: {} ms end to end (server {} ms, solver {} ms), {} pinned, {} vans changed", run,
                        elapsed / 1_000_000, update.get("totalMillis").asLong(), update.get("solveMillis").asLong(),
                        update.get("pinnedStops").asInt(), update.get("changedVehicles").asInt());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("runs", runs);
        report.put("ordersAssigned", assigned);
        report.put("endToEnd", endToEnd.summary());
        report.put("serverPipeline", server.summary());
        report.put("solver", solver.summary());
        report.put("samples", samples);
        Path out = Path.of(args.string("out", "benchmarks/results"), "replan-api.json");
        Files.createDirectories(out.getParent());
        ApiClient.JSON.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), report);
        log.info("Wrote {}", out.toAbsolutePath());
    }

    private static boolean isAssigned(JsonNode plan, String orderRef) {
        for (JsonNode route : plan.get("routes")) {
            for (JsonNode stop : route.get("stops")) {
                if (stop.get("orderRef").asText().equals(orderRef)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ReplanBenchMode() {
    }
}
