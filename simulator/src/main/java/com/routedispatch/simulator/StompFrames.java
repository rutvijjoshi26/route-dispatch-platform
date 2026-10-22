package com.routedispatch.simulator;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal STOMP 1.2 text frame codec, enough to subscribe and read messages. */
final class StompFrames {

    record Frame(String command, Map<String, String> headers, String body) {
    }

    static String encode(String command, Map<String, String> headers, String body) {
        StringBuilder frame = new StringBuilder(command).append('\n');
        headers.forEach((k, v) -> frame.append(k).append(':').append(v).append('\n'));
        return frame.append('\n').append(body).append('\u0000').toString();
    }

    /** Returns null for heartbeats (bare newlines). */
    static Frame decode(String raw) {
        String text = raw.endsWith("\u0000") ? raw.substring(0, raw.length() - 1) : raw;
        text = text.replaceFirst("^\\n+", "");
        if (text.isEmpty()) {
            return null;
        }
        int headerEnd = text.indexOf("\n\n");
        String head = headerEnd < 0 ? text : text.substring(0, headerEnd);
        String body = headerEnd < 0 ? "" : text.substring(headerEnd + 2);
        String[] lines = head.split("\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.putIfAbsent(lines[i].substring(0, colon), lines[i].substring(colon + 1));
            }
        }
        return new Frame(lines[0], headers, body);
    }

    private StompFrames() {
    }
}
