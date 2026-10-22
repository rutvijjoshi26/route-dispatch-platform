package com.routedispatch.simulator;

import com.routedispatch.simulator.ApiModels.DriverRoute;
import com.routedispatch.simulator.ApiModels.RouteStop;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One simulated driver: follows the planned route along its road geometry on the accelerated plan clock, sends a
 * GPS ping every tick, reports EN_ROUTE / ARRIVED / DELIVERED / FAILED, and picks up re-plans by polling the
 * route with its ETag. Stops it has started are pinned by the server, so a re-plan never changes its current leg.
 */
final class DriverAgent implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DriverAgent.class);
    private static final int POLL_EVERY_TICKS = 3;
    private static final LocalTime GIVE_UP_AT = LocalTime.of(21, 0);
    private static final int RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 250;

    private final ApiClient api;
    private final String token;
    private final DriveOptions options;
    private final Random random;

    private DriverRoute route;
    private String etag;
    private Instant clockFetchedAt;
    private double lat;
    private double lon;
    private int ticks;
    private boolean stalled;

    DriverAgent(ApiClient api, String token, DriveOptions options, Random random) {
        this.api = api;
        this.token = token;
        this.options = options;
        this.random = random;
    }

    @Override
    public void run() {
        try {
            refresh(true);
            lat = route.depotLat();
            lon = route.depotLon();
            idleUntil(route.departDepot());
            while (!Thread.currentThread().isInterrupted() && planNow().isBefore(GIVE_UP_AT)) {
                try {
                    Optional<RouteStop> next = RouteFollower.nextStop(route);
                    if (next.isEmpty()) {
                        if (!atDepot()) {
                            driveHome();
                        }
                        idleTicks(10);
                        refresh(false);
                        continue;
                    }
                    serve(next.get());
                    refresh(true);
                } catch (ApiClient.ApiException e) {
                    // For example the stop was moved to another van by a re-plan a moment ago: re-read and carry on.
                    log.warn("[{}] {}; reloading route", name(), e.getMessage());
                    Thread.sleep(options.pingIntervalMillis());
                    refresh(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("[{}] stopped: {}", name(), e.getMessage());
        }
    }

    private void serve(RouteStop stop) throws InterruptedException {
        if ("PENDING".equals(stop.status())) {
            report(stop, "EN_ROUTE", null);
        }
        if (!"ARRIVED".equals(stop.status())) {
            drive(stop);
            report(stop, "ARRIVED", null);
        }
        idleUntil(stop.windowStart());
        idlePlanSeconds(stop.serviceSeconds());
        boolean failed = random.nextDouble() < options.failureRate();
        report(stop, failed ? "FAILED" : "DELIVERED", failed ? "Customer not available" : null);
        log.info("[{}] {} {} {} ({} of {})", name(), planNow().withNano(0), failed ? "FAILED   " : "delivered",
                stop.orderRef(), stop.sequence() + 1, route.stops().size());
    }

    /** One logical event: retried with the same event id and timestamp until the server confirms it. */
    private void report(RouteStop stop, String type, String note) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        Retry.call(RETRY_ATTEMPTS, RETRY_BACKOFF_MILLIS,
                () -> api.postEvent(token, stop.id(), eventId, type, occurredAt, note));
    }

    /** Pings are best effort: a lost one is replaced by the next tick, so failures only get logged. */
    private void ping(double speedMps, double heading) {
        UUID pingId = UUID.randomUUID();
        Instant recordedAt = Instant.now();
        try {
            Retry.call(RETRY_ATTEMPTS, RETRY_BACKOFF_MILLIS,
                    () -> api.postPing(token, pingId, lat, lon, Math.min(speedMps, 40), heading, recordedAt));
        } catch (ApiClient.ApiException e) {
            log.warn("[{}] ping not delivered: {}", name(), e.getMessage());
        }
    }

    private void drive(RouteStop stop) throws InterruptedException {
        List<double[]> path = stop.inboundLeg().isEmpty()
                ? List.of(new double[] {lat, lon}, new double[] {lat, lon}) : stop.inboundLeg();
        double factor = 1 + (random.nextDouble() * 2 - 1) * options.speedJitter();
        long legSeconds = Math.round(RouteFollower.plannedLegSeconds(route, stop) * factor);
        followPath(new PathWalker(path), legSeconds, maybeStall());
    }

    private void driveHome() throws InterruptedException {
        List<double[]> path = route.returnLeg().size() < 2
                ? List.of(new double[] {lat, lon}, new double[] {route.depotLat(), route.depotLon()}) : route.returnLeg();
        PathWalker walker = new PathWalker(path);
        followPath(walker, Math.max(RouteFollower.MIN_LEG_SECONDS, Math.round(walker.lengthMeters() / 8.0)), 0);
        log.info("[{}] {} back at depot", name(), planNow().withNano(0));
    }

    /** Traffic jam for the configured late van: returns plan seconds to stand still in the middle of the leg. */
    private long maybeStall() {
        if (stalled || options.lateVan() == null || !options.lateVan().equalsIgnoreCase(route.vehicleName())
                || planNow().isBefore(options.lateAfter())) {
            return 0;
        }
        stalled = true;
        log.info("[{}] {} stuck in traffic for {} min", name(), planNow().withNano(0), options.lateMinutes());
        return options.lateMinutes() * 60L;
    }

    /** Moves along the path over {@code legSeconds} of plan time, standing still for {@code stallSeconds} halfway. */
    private void followPath(PathWalker walker, long legSeconds, long stallSeconds) throws InterruptedException {
        double speed = walker.lengthMeters() / Math.max(1, legSeconds);
        long totalSeconds = legSeconds + stallSeconds;
        LocalTime start = planNow();
        while (true) {
            long elapsed = planNow().toSecondOfDay() - start.toSecondOfDay();
            if (elapsed >= totalSeconds) {
                break;
            }
            long moving = elapsed < legSeconds / 2 ? elapsed : Math.max(legSeconds / 2, elapsed - stallSeconds);
            boolean stopped = elapsed >= legSeconds / 2 && elapsed < legSeconds / 2 + stallSeconds;
            PathWalker.Position position = walker.at(speed * moving);
            lat = position.lat();
            lon = position.lon();
            tick(stopped ? 0 : speed, position.headingDeg());
        }
        PathWalker.Position end = walker.at(walker.lengthMeters());
        lat = end.lat();
        lon = end.lon();
        ping(0, end.headingDeg());
    }

    private void idleUntil(LocalTime planTime) throws InterruptedException {
        while (planTime != null && planNow().isBefore(planTime)) {
            tick(0, 0);
        }
    }

    private void idlePlanSeconds(long seconds) throws InterruptedException {
        idleUntil(planNow().plusSeconds(seconds));
    }

    private void idleTicks(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            tick(0, 0);
        }
    }

    /** One ping interval: report position, sleep, and every few ticks check for a re-planned route. */
    private void tick(double speedMps, double heading) throws InterruptedException {
        ping(speedMps, heading);
        Thread.sleep(options.pingIntervalMillis());
        if (++ticks % POLL_EVERY_TICKS == 0) {
            refresh(false);
        }
    }

    private void refresh(boolean force) {
        ApiClient.RouteResponse response = api.route(token, force ? null : etag);
        etag = response.etag();
        response.route().ifPresent(fresh -> {
            if (route != null && fresh.planVersion() != route.planVersion()) {
                log.info("[{}] route updated to plan version {} ({} stops)", name(), fresh.planVersion(), fresh.stops().size());
            }
            route = fresh;
            clockFetchedAt = Instant.now();
        });
    }

    private LocalTime planNow() {
        double elapsed = Duration.between(clockFetchedAt, Instant.now()).toMillis() / 1000.0 * route.clock().timeScale();
        long seconds = Math.min(LocalTime.MAX.toSecondOfDay(), route.clock().now().toSecondOfDay() + Math.round(elapsed));
        return LocalTime.ofSecondOfDay(seconds);
    }

    private boolean atDepot() {
        return PathWalker.meters(new double[] {lat, lon}, new double[] {route.depotLat(), route.depotLon()}) < 50;
    }

    private String name() {
        return route == null ? "driver" : route.vehicleName();
    }
}
