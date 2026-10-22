package com.routedispatch.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.routedispatch.demo.ChicagoDayGenerator;
import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class TrackingIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    TestFleet.Fleet fleet;
    String dispatcher;
    String driver1;
    String driver2;
    long planId;
    long van1;
    long van2;
    JsonNode plan;

    /** Each test gets its own depot, two vans with tight capacity (so both are used) and a dispatched day. */
    @BeforeEach
    void dispatchADay() throws Exception {
        fleet = testFleet.create(2, 16);
        van1 = fleet.vans().get(0).getId();
        van2 = fleet.vans().get(1).getId();
        dispatcher = api.login(fleet.dispatcherEmail());
        driver1 = api.login(fleet.driverEmails().get(0));
        driver2 = api.login(fleet.driverEmails().get(1));
        planId = api.read(api.post(dispatcher, "/api/plans", Map.of("name", "Tracking", "serviceDate",
                LocalDate.now().toString(), "depotId", fleet.depot().getId()))).get("id").asLong();
        StringBuilder csv = new StringBuilder("order_ref,customer,address,lat,lon,window_start,window_end,size\n");
        ChicagoDayGenerator.generate(9, 16, 2).orders().forEach(o -> csv.append("%s,%s,\"%s\",%s,%s,08:00,18:00,%s%n"
                .formatted(o.orderRef(), o.customerName(), o.address(), o.point().lat(), o.point().lon(), o.size())));
        mvc.perform(multipart("/api/plans/" + planId + "/orders/import")
                .file(new MockMultipartFile("file", "o.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + dispatcher)).andExpect(status().isOk());
        api.post(dispatcher, "/api/plans/" + planId + "/optimize", null).andExpect(status().isAccepted());
        api.awaitJson(dispatcher, "/api/plans/" + planId, p -> p.get("status").asText().equals("PLANNED"), 30_000);
        plan = api.read(api.post(dispatcher, "/api/plans/" + planId + "/dispatch", Map.of("timeScale", 1)).andExpect(status().isOk()));
    }

    List<Long> stopsOf(long vehicleId) {
        for (JsonNode route : plan.get("routes")) {
            if (route.get("vehicleId").asLong() == vehicleId) {
                return route.get("stops").findValues("id").stream().map(JsonNode::asLong).toList();
            }
        }
        throw new IllegalStateException("no route");
    }

    static Map<String, Object> ping(UUID id, Instant at, double lat, double lon) {
        return Map.of("pingId", id.toString(), "recordedAt", at.toString(), "lat", lat, "lon", lon, "speedMps", 8.5, "headingDeg", 90);
    }

    static Map<String, Object> event(UUID id, String type) {
        return Map.of("eventId", id.toString(), "type", type, "occurredAt", Instant.now().toString());
    }

    @Test
    void duplicatePingIsStoredOnce() throws Exception {
        Map<String, Object> ping = ping(UUID.randomUUID(), Instant.now(), 41.90, -87.68);

        api.post(driver1, "/api/driver/positions", Map.of("pings", List.of(ping))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));
        api.post(driver1, "/api/driver/positions", Map.of("pings", List.of(ping))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_ping WHERE ping_id = ?::uuid", Integer.class,
                ping.get("pingId"))).isEqualTo(1);
    }

    @Test
    void olderPingArrivingLateDoesNotMoveTheVanBackwards() throws Exception {
        Instant now = Instant.now();
        api.post(driver2, "/api/driver/positions", Map.of("pings", List.of(ping(UUID.randomUUID(), now, 41.95, -87.70))))
                .andExpect(jsonPath("$.positionUpdated").value(true));
        api.post(driver2, "/api/driver/positions", Map.of("pings", List.of(ping(UUID.randomUUID(), now.minusSeconds(30), 41.80, -87.60))))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.positionUpdated").value(false));

        JsonNode positions = api.read(api.get(dispatcher, "/api/plans/" + planId + "/positions").andExpect(status().isOk()));
        JsonNode van = null;
        for (JsonNode p : positions) {
            if (p.get("vehicleId").asLong() == van2) {
                van = p;
            }
        }
        assertThat(van).isNotNull();
        assertThat(van.get("lat").asDouble()).isEqualTo(41.95);
    }

    @Test
    void pingsFromTheFarFutureAreRejected() throws Exception {
        api.post(driver1, "/api/driver/positions",
                        Map.of("pings", List.of(ping(UUID.randomUUID(), Instant.now().plusSeconds(3600), 41.9, -87.7))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEventReturnsTheOriginalOutcomeAndIsStoredOnce() throws Exception {
        long stopId = stopsOf(van1).getFirst();
        Map<String, Object> enRoute = event(UUID.randomUUID(), "EN_ROUTE");

        api.post(driver1, "/api/driver/stops/" + stopId + "/events", enRoute).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.status").value("EN_ROUTE"));
        api.post(driver1, "/api/driver/stops/" + stopId + "/events", enRoute).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stop_event WHERE stop_id = ?", Integer.class, stopId)).isEqualTo(1);
    }

    @Test
    void eventsArrivingOutOfOrderNeverMoveAStopBackwards() throws Exception {
        long stopId = stopsOf(van1).get(1);

        api.post(driver1, "/api/driver/stops/" + stopId + "/events", event(UUID.randomUUID(), "DELIVERED"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        api.post(driver1, "/api/driver/stops/" + stopId + "/events", event(UUID.randomUUID(), "ARRIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED_STALE"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void driverCannotReportOnAnotherVansStop() throws Exception {
        long otherVansStop = stopsOf(van2).getFirst();

        api.post(driver1, "/api/driver/stops/" + otherVansStop + "/events", event(UUID.randomUUID(), "ARRIVED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesAreEnforcedOnEveryApi() throws Exception {
        api.get(null, "/api/plans").andExpect(status().isUnauthorized());
        api.get(driver1, "/api/plans").andExpect(status().isForbidden());
        api.get(dispatcher, "/api/driver/route").andExpect(status().isForbidden());
        api.get("not-a-token", "/api/plans").andExpect(status().isUnauthorized());
    }

    @Test
    void driverRouteSupportsConditionalRequests() throws Exception {
        String etag = api.get(driver1, "/api/driver/route").andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(van1))
                .andExpect(jsonPath("$.stops[0].inboundLeg").isArray())
                .andExpect(jsonPath("$.clock.timeScale").value(1.0))
                .andReturn().getResponse().getHeader("ETag");

        assertThat(etag).isNotBlank();
        mvc.perform(get("/api/driver/route").header("Authorization", "Bearer " + driver1).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void nearestVansAreOrderedByDistance() throws Exception {
        Instant now = Instant.now().plusSeconds(1);
        api.post(driver1, "/api/driver/positions", Map.of("pings", List.of(ping(UUID.randomUUID(), now, 41.88, -87.63))));
        api.post(driver2, "/api/driver/positions", Map.of("pings", List.of(ping(UUID.randomUUID(), now, 41.97, -87.70))));

        api.get(dispatcher, "/api/plans/" + planId + "/vehicles/nearest?lat=41.881&lon=-87.631")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(van1))
                .andExpect(jsonPath("$[1].vehicleId").value(van2))
                .andExpect(jsonPath("$[0].distanceMeters").isNumber());
    }

    @Test
    void trailFollowsPingsInRecordedOrder() throws Exception {
        Instant base = Instant.now().plusSeconds(5);
        List<Map<String, Object>> pings = List.of(
                ping(UUID.randomUUID(), base.plusSeconds(20), 41.92, -87.66),
                ping(UUID.randomUUID(), base, 41.90, -87.66),
                ping(UUID.randomUUID(), base.plusSeconds(10), 41.91, -87.66));
        api.post(driver1, "/api/driver/positions", Map.of("pings", pings)).andExpect(jsonPath("$.accepted").value(3));

        JsonNode trail = api.read(api.get(dispatcher, "/api/plans/" + planId + "/vehicles/" + van1 + "/trail?since=" + base)
                .andExpect(status().isOk()));
        assertThat(trail.get("path")).hasSize(3);
        assertThat(trail.at("/path/0/0").asDouble()).isEqualTo(41.90);
        assertThat(trail.at("/path/2/0").asDouble()).isEqualTo(41.92);
    }

    @Test
    void validationRejectsMalformedPings() throws Exception {
        api.post(driver1, "/api/driver/positions", Map.of("pings", List.of(Map.of("pingId", UUID.randomUUID().toString(),
                        "recordedAt", Instant.now().toString(), "lat", 123.0, "lon", -87.7))))
                .andExpect(status().isBadRequest());
        api.post(driver1, "/api/driver/positions", Map.of("pings", List.of())).andExpect(status().isBadRequest());
    }

    @Test
    void everyDriverGetsAnEtagForTheirOwnRoute() throws Exception {
        api.get(driver2, "/api/driver/route").andExpect(status().isOk()).andExpect(header().exists("ETag"));
    }
}
