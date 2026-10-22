package com.routedispatch.simulator;

/**
 * Driver simulator and API load tools.
 * <pre>
 *   drive   --prepare --time-scale 30 --drivers 8   simulate the demo fleet driving its routes
 *   load    --levels 50,100,200 --rate 1 --duration 60   GPS ingest throughput and latency
 *   replan  --runs 10                                    add orders to a dispatched day and time re-plans
 * </pre>
 * Common option: {@code --api http://localhost:8080}.
 */
public final class SimulatorApp {

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "drive" : args[0];
        Args options = new Args(args, args.length == 0 ? 0 : 1);
        switch (mode) {
            case "drive" -> DriveMode.run(options);
            case "load" -> LoadTestMode.run(options);
            case "replan" -> ReplanBenchMode.run(options);
            default -> {
                System.err.println("Usage: driver-simulator [drive|load|replan] [--option value ...]");
                System.exit(2);
            }
        }
    }

    private SimulatorApp() {
    }
}
