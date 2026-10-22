package com.routedispatch.simulator;

import java.util.HashMap;
import java.util.Map;

/** {@code --key value} command-line options with typed getters and defaults. */
final class Args {

    private final Map<String, String> values = new HashMap<>();

    Args(String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument " + args[i]);
            }
            String key = args[i].substring(2);
            boolean flag = i + 1 >= args.length || args[i + 1].startsWith("--");
            values.put(key, flag ? "true" : args[++i]);
        }
    }

    String string(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    int integer(String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
    }

    double decimal(String key, double fallback) {
        return values.containsKey(key) ? Double.parseDouble(values.get(key)) : fallback;
    }

    boolean flag(String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }
}
