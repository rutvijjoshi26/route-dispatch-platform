package com.routedispatch.simulator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Dispatcher-side STOMP subscription over the JDK WebSocket client, used to time what dispatchers see. */
final class StompSubscriber implements AutoCloseable {

    private final WebSocket socket;
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private int nextId;

    /** {@code onMessage} receives (destination, body) for every MESSAGE frame. */
    StompSubscriber(String httpBaseUrl, String token, BiConsumer<String, String> onMessage) throws Exception {
        String wsUrl = httpBaseUrl.replaceFirst("^http", "ws") + "/ws";
        socket = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(wsUrl), new Listener(onMessage)).get(10, TimeUnit.SECONDS);
        socket.sendText(StompFrames.encode("CONNECT", Map.of("accept-version", "1.2", "host", "localhost",
                "heart-beat", "0,0", "Authorization", "Bearer " + token), ""), true);
        connected.get(10, TimeUnit.SECONDS);
    }

    synchronized void subscribe(String destination) {
        socket.sendText(StompFrames.encode("SUBSCRIBE", Map.of("id", "sub-" + nextId++, "destination", destination), ""), true);
    }

    @Override
    public void close() {
        socket.sendText(StompFrames.encode("DISCONNECT", Map.of(), ""), true);
        socket.abort();
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        private final BiConsumer<String, String> onMessage;

        Listener(BiConsumer<String, String> onMessage) {
            this.onMessage = onMessage;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            int end;
            while ((end = buffer.indexOf("\u0000")) >= 0) {
                StompFrames.Frame frame = StompFrames.decode(buffer.substring(0, end + 1));
                buffer.delete(0, end + 1);
                if (frame == null) {
                    continue;
                }
                switch (frame.command()) {
                    case "CONNECTED" -> connected.complete(null);
                    case "MESSAGE" -> onMessage.accept(frame.headers().get("destination"), frame.body());
                    case "ERROR" -> connected.completeExceptionally(new IllegalStateException(frame.headers().get("message")));
                    default -> { }
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.completeExceptionally(error);
        }
    }
}
