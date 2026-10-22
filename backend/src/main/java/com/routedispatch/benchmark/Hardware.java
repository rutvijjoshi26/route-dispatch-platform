package com.routedispatch.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Describes the machine the benchmark ran on. */
final class Hardware {

    static Map<String, Object> describe() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cpu", cpuName());
        info.put("logicalCores", Runtime.getRuntime().availableProcessors());
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        info.put("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        info.put("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        return info;
    }

    private static String cpuName() {
        String[] command = System.getProperty("os.name").toLowerCase().contains("mac")
                ? new String[] {"sysctl", "-n", "machdep.cpu.brand_string"}
                : new String[] {"sh", "-c", "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2"};
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                return "unknown";
            }
            String name = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? "unknown" : name;
        } catch (IOException e) {
            return "unknown";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private Hardware() {
    }
}
