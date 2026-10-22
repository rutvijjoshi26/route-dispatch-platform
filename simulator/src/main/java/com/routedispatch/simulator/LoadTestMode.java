package com.routedispatch.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPS ingest load test. Each simulated device is its own van ({@code load<n>@demo.local}, created by the demo
 * profile with LOAD_TEST_DRIVERS) and sends pings on a fixed schedule (open loop, so slow responses do not
 * reduce the offered load). Measures HTTP latency per ping and end-to-end latency from ping timestamp to the
 * position arriving at a subscribed dispatcher over STOMP.
 */
final class LoadTestMode {

    private static final Logger log = LoggerFactory.getLogger(LoadTestMode.class);

    static void run(Args args) throws Exception {
        ApiClient api = new ApiClient(args.string("api", "http://localhost:8080"));
        double rate = args.decimal("rate", 1.0);
        int seconds = args.integer("duration", 60);
        int warmup = args.integer("warmup", 10);
        List<Map<String, Object>> levels = new ArrayList<>();
        DispatcherSession dispatcher = new DispatcherSession(api, args.string("dispatcher", "dispatcher@demo.local"),
                args.string("dispatcher-password", "dispatch-demo"));
        long planId = dispatcher.findPlan("Load test");
        for (String level : args.string("levels", "50").split(",")) {
            int drivers = Integer.parseInt(level.trim());
            levels.add(runLevel(api, dispatcher, planId, drivers, rate, warmup, seconds, args.string("password", "driver-demo")));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("api", api.baseUrl());
        report.put("pingsPerDriverPerSecond", rate);
        report.put("measuredSecondsPerLevel", seconds);
        report.put("warmupSecondsPerLevel", warmup);
        report.put("levels", levels);
        String rateLabel = rate == Math.rint(rate) ? String.valueOf((long) rate) : String.valueOf(rate);
        Path out = Path.of(args.string("out", "benchmarks/results"), "ingest-load-" + rateLabel + "-per-second.json");
        Files.createDirectories(out.getParent());
        ApiClient.JSON.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), report);
        log.info("Wrote {}", out.toAbsolutePath());
    }

    private static Map<String, Object> runLevel(ApiClient api, DispatcherSession dispatcher, long planId, int drivers,
                                                double rate, int warmup, int seconds, String password) throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= drivers; i++) {
            tokens.add(api.login("load" + i + "@demo.local", password));
        }
        LatencyStats http = new LatencyStats();
        LatencyStats endToEnd = new LatencyStats();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger sent = new AtomicInteger();
        AtomicLong measureFrom = new AtomicLong(Long.MAX_VALUE);
        Instant start = Instant.now();
        Instant measureStart = start.plusSeconds(warmup);
        Instant end = measureStart.plusSeconds(seconds);
        measureFrom.set(measureStart.toEpochMilli());

        try (StompSubscriber subscriber = new StompSubscriber(api.baseUrl(), dispatcher.token(), (destination, body) -> {
            try {
                JsonNode position = ApiClient.JSON.readTree(body);
                Instant recorded = Instant.parse(position.get("recordedAt").asText());
                if (recorded.toEpochMilli() >= measureFrom.get() && recorded.isBefore(end)) {
                    endToEnd.record(Duration.between(recorded, Instant.now()).toNanos());
                }
            } catch (java.io.IOException e) {
                errors.incrementAndGet();
            }
        })) {
            subscriber.subscribe("/topic/plans/" + planId + "/positions");
            Thread.sleep(500);
            long periodNanos = (long) (1_000_000_000L / rate);
            List<Thread> threads = new ArrayList<>();
            for (int d = 0; d < drivers; d++) {
                String token = tokens.get(d);
                double baseLat = 41.80 + (d % 20) * 0.01;
                double baseLon = -87.75 + (d / 20) * 0.01;
                long offset = periodNanos * d / drivers;
                threads.add(Thread.ofVirtual().start(() -> {
                    long next = System.nanoTime() + offset;
                    int i = 0;
                    while (Instant.now().isBefore(end)) {
                        long wait = next - System.nanoTime();
                        if (wait > 0) {
                            try {
                                Thread.sleep(Duration.ofNanos(wait));
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                        next += periodNanos;
                        Instant now = Instant.now();
                        long t0 = System.nanoTime();
                        try {
                            api.postPing(token, baseLat + i * 0.00001, baseLon, 9, 0, now);
                            if (!now.isBefore(measureStart)) {
                                http.record(System.nanoTime() - t0);
                                sent.incrementAndGet();
                            }
                        } catch (ApiClient.ApiException e) {
                            if (!now.isBefore(measureStart)) {
                                errors.incrementAndGet();
                            }
                        }
                        i++;
                    }
                }));
            }
            for (Thread thread : threads) {
                thread.join();
            }
            Thread.sleep(1_000);
        }
        LatencyStats.Summary httpSummary = http.summary();
        LatencyStats.Summary e2eSummary = endToEnd.summary();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("drivers", drivers);
        result.put("offeredPingsPerSecond", drivers * rate);
        result.put("acceptedPingsPerSecond", Math.round(sent.get() * 10.0 / seconds) / 10.0);
        result.put("errors", errors.get());
        result.put("httpLatency", httpSummary);
        result.put("pingToDispatcherLatency", e2eSummary);
        log.info("{} drivers: {} pings/s accepted (offered {}), {} errors, HTTP p50 {} ms p95 {} ms p99 {} ms, "
                        + "ping-to-dispatcher p95 {} ms ({} messages)", drivers, result.get("acceptedPingsPerSecond"),
                drivers * rate, errors.get(), httpSummary.p50Millis(), httpSummary.p95Millis(), httpSummary.p99Millis(),
                e2eSummary.p95Millis(), e2eSummary.count());
        return result;
    }

    private LoadTestMode() {
    }
}
