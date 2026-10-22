package com.routedispatch.simulator;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the demo fleet: optionally optimizes and dispatches the demo day, then runs one {@link DriverAgent} per
 * driver account on a virtual thread until the day is done or the process is stopped.
 */
final class DriveMode {

    private static final Logger log = LoggerFactory.getLogger(DriveMode.class);

    static void run(Args args) throws Exception {
        ApiClient api = new ApiClient(args.string("api", "http://localhost:8080"));
        int drivers = args.integer("drivers", 8);
        DriveOptions options = new DriveOptions(
                args.integer("ping-interval-ms", 1000),
                args.decimal("speed-jitter", 0.15),
                args.decimal("failure-rate", 0.03),
                args.string("late-van", "Van 3"),
                args.integer("late-minutes", 45),
                LocalTime.parse(args.string("late-after", "10:30")));
        if (args.flag("prepare")) {
            DispatcherSession dispatcher = new DispatcherSession(api, args.string("dispatcher", "dispatcher@demo.local"),
                    args.string("dispatcher-password", "dispatch-demo"));
            long planId = dispatcher.findPlan(args.string("plan", "Chicago demo day"));
            dispatcher.ensureDispatched(planId, args.decimal("time-scale", 30));
        }
        Random seeds = new Random(args.integer("seed", 7));
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= drivers; i++) {
            String token = api.login("driver" + i + "@demo.local", args.string("password", "driver-demo"));
            DriverAgent agent = new DriverAgent(api, token, options, new Random(seeds.nextLong()));
            threads.add(Thread.ofVirtual().name("driver-" + i).start(agent));
        }
        log.info("{} drivers on the road (ping every {} ms, late van: {} +{} min after {})", drivers,
                options.pingIntervalMillis(), options.lateVan(), options.lateMinutes(), options.lateAfter());
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private DriveMode() {
    }
}
