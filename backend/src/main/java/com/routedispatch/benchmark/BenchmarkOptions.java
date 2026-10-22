package com.routedispatch.benchmark;

/** Command-line options for {@link BenchmarkApp}. */
record BenchmarkOptions(String provider, String osrmUrl, long seed, int stops, int vans, int solveSeconds,
                        int replanRuns, String outDir) {

    static BenchmarkOptions parse(String[] args) {
        String provider = "osrm";
        String osrmUrl = "http://localhost:5001";
        long seed = 42;
        int stops = 200;
        int vans = 8;
        int solveSeconds = 60;
        int replanRuns = 10;
        String outDir = "benchmarks/results";
        for (int i = 0; i < args.length; i++) {
            String value = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--provider" -> provider = value;
                case "--osrm-url" -> osrmUrl = value;
                case "--seed" -> seed = Long.parseLong(value);
                case "--stops" -> stops = Integer.parseInt(value);
                case "--vans" -> vans = Integer.parseInt(value);
                case "--solve-seconds" -> solveSeconds = Integer.parseInt(value);
                case "--replan-runs" -> replanRuns = Integer.parseInt(value);
                case "--out" -> outDir = value;
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
            i++;
        }
        return new BenchmarkOptions(provider, osrmUrl, seed, stops, vans, solveSeconds, replanRuns, outDir);
    }
}
