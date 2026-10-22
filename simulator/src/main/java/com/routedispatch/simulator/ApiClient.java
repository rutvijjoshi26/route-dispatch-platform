package com.routedispatch.simulator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.routedispatch.simulator.ApiModels.DriverRoute;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

/** Talks to the backend REST API exactly like a driver device or dispatcher client would. */
final class ApiClient {

    static final class ApiException extends RuntimeException {
        final int status;

        ApiException(int status, String message) {
            super("HTTP " + status + ": " + message);
            this.status = status;
        }
    }

    record RouteResponse(Optional<DriverRoute> route, String etag) {
    }

    static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final String baseUrl;
    private final HttpClient http;

    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    String baseUrl() {
        return baseUrl;
    }

    String login(String email, String password) {
        return post(null, "/api/auth/login", Map.of("email", email, "password", password)).get("token").asText();
    }

    RouteResponse route(String token, String etag) {
        HttpRequest.Builder request = request(token, "/api/driver/route").GET();
        if (etag != null) {
            request.header("If-None-Match", etag);
        }
        HttpResponse<String> response = send(request.build());
        String newEtag = response.headers().firstValue("ETag").orElse(etag);
        if (response.statusCode() == 304) {
            return new RouteResponse(Optional.empty(), newEtag);
        }
        return new RouteResponse(Optional.of(read(response.body(), DriverRoute.class)), newEtag);
    }

    /** Sends one ping; retries reuse {@code pingId}, so the server stores it once. */
    JsonNode postPing(String token, UUID pingId, double lat, double lon, double speedMps, double headingDeg,
                      Instant recordedAt) {
        Map<String, Object> ping = Map.of("pingId", pingId.toString(), "recordedAt", recordedAt.toString(),
                "lat", lat, "lon", lon, "speedMps", speedMps, "headingDeg", headingDeg);
        return post(token, "/api/driver/positions", Map.of("pings", List.of(ping)));
    }

    JsonNode postPing(String token, double lat, double lon, double speedMps, double headingDeg, Instant recordedAt) {
        return postPing(token, UUID.randomUUID(), lat, lon, speedMps, headingDeg, recordedAt);
    }

    /** Reports a stop event; retries reuse {@code eventId}, so the server applies it once. */
    JsonNode postEvent(String token, long stopId, UUID eventId, String type, Instant occurredAt, String note) {
        Map<String, Object> event = new java.util.HashMap<>(Map.of("eventId", eventId.toString(), "type", type,
                "occurredAt", occurredAt.toString()));
        if (note != null) {
            event.put("note", note);
        }
        return post(token, "/api/driver/stops/" + stopId + "/events", event);
    }

    JsonNode get(String token, String path) {
        HttpResponse<String> response = send(request(token, path).GET().build());
        return read(response.body(), JsonNode.class);
    }

    JsonNode post(String token, String path, Object body) {
        HttpRequest.BodyPublisher publisher;
        try {
            publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        HttpResponse<String> response = send(request(token, path).header("Content-Type", "application/json")
                .POST(publisher).build());
        return response.body().isBlank() ? JSON.createObjectNode() : read(response.body(), JsonNode.class);
    }

    private HttpRequest.Builder request(String token, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(), response.body());
            }
            return response;
        } catch (IOException e) {
            throw new ApiException(0, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "interrupted");
        }
    }

    private static <T> T read(String body, Class<T> type) {
        try {
            return JSON.readValue(body, type);
        } catch (IOException e) {
            throw new ApiException(0, "Unreadable response: " + e.getMessage());
        }
    }
}
