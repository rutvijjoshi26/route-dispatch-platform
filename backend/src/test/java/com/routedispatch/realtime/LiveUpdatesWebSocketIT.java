package com.routedispatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.routedispatch.demo.ChicagoDayGenerator;
import com.routedispatch.support.PostgisTestcontainer;
import com.routedispatch.support.TestFleet;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real server on a random port, real STOMP client: authentication, authorization and live position push. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgisTestcontainer.class, TestFleet.class})
@ActiveProfiles("test")
class LiveUpdatesWebSocketIT {

    @LocalServerPort int port;
    @Autowired TestFleet testFleet;

    RestClient http;
    WebSocketStompClient stomp;

    @BeforeEach
    void setUp() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());
    }

    String login(String email) {
        return http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", TestFleet.PASSWORD)).retrieve().body(JsonNode.class)
                .get("token").asText();
    }

    StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    /** Completes with the first message on a subscription. */
    static final class FirstMessage implements StompFrameHandler {
        final CompletableFuture<JsonNode> message = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return JsonNode.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            message.complete((JsonNode) payload);
        }
    }

    /** Session-level handler: the broker reports refused frames as STOMP ERROR frames delivered here. */
    static final class ErrorWatcher extends StompSessionHandlerAdapter {
        final CompletableFuture<String> error = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            error.complete(headers.getFirst("message"));
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            error.complete(exception.getMessage());
        }
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        assertThatThrownBy(() -> connect("garbage")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void driverCannotSubscribeToDispatcherTopics() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(1, 10);
        ErrorWatcher watcher = new ErrorWatcher();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + login(fleet.driverEmails().getFirst()));
        StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, watcher).get(5, TimeUnit.SECONDS);
        FirstMessage messages = new FirstMessage();

        session.subscribe("/topic/plans/1/positions", messages);

        assertThat(watcher.error.get(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(messages.message).isNotDone();
    }

    @Test
    void dispatcherReceivesADriversPositionLive() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(1, 40);
        String dispatcher = login(fleet.dispatcherEmail());
        String driver = login(fleet.driverEmails().getFirst());
        long planId = dispatchedPlan(fleet, dispatcher);

        FirstMessage received = new FirstMessage();
        StompSession session = connect(dispatcher);
        session.subscribe("/topic/plans/" + planId + "/positions", received);
        Thread.sleep(300);

        http.post().uri("/api/driver/positions").header("Authorization", "Bearer " + driver)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("pings", List.of(Map.of("pingId", UUID.randomUUID().toString(),
                        "recordedAt", Instant.now().toString(), "lat", 41.9001, "lon", -87.6702))))
                .retrieve().toBodilessEntity();

        JsonNode position = received.message.get(5, TimeUnit.SECONDS);
        assertThat(position.get("vehicleId").asLong()).isEqualTo(fleet.vans().getFirst().getId());
        assertThat(position.get("lat").asDouble()).isEqualTo(41.9001);
        session.disconnect();
    }

    long dispatchedPlan(TestFleet.Fleet fleet, String token) throws Exception {
        long planId = http.post().uri("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "WS", "serviceDate", LocalDate.now().toString(), "depotId", fleet.depot().getId()))
                .retrieve().body(JsonNode.class).get("id").asLong();
        StringBuilder csv = new StringBuilder("order_ref,customer,address,lat,lon,window_start,window_end,size\n");
        ChicagoDayGenerator.generate(5, 5, 1).orders().forEach(o -> csv.append("%s,%s,\"%s\",%s,%s,08:00,18:00,%s%n"
                .formatted(o.orderRef(), o.customerName(), o.address(), o.point().lat(), o.point().lon(), o.size())));
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(csv.toString().getBytes()) {
            @Override
            public String getFilename() {
                return "orders.csv";
            }
        });
        http.post().uri("/api/plans/" + planId + "/orders/import").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
        http.post().uri("/api/plans/" + planId + "/optimize").header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode plan = http.get().uri("/api/plans/" + planId).header("Authorization", "Bearer " + token)
                    .retrieve().body(JsonNode.class);
            if (plan.get("status").asText().equals("PLANNED")) {
                break;
            }
            Thread.sleep(200);
        }
        http.post().uri("/api/plans/" + planId + "/dispatch").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("timeScale", 1)).retrieve().toBodilessEntity();
        return planId;
    }
}
