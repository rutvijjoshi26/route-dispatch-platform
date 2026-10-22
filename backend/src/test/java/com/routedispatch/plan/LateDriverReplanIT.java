package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.routedispatch.demo.ChicagoDayGenerator;
import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A driver who reaches a stop much later than planned triggers a DRIVER_LATE re-plan. Stops the driver already
 * handled stay first on the route, in the same order.
 */
@IntegrationTest
class LateDriverReplanIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;
    @Autowired MockMvc mvc;

    @Test
    void lateArrivalReplansTheRestOfTheDayAndKeepsVisitedStopsPinned() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(2, 16);
        String dispatcher = api.login(fleet.dispatcherEmail());
        String driver = api.login(fleet.driverEmails().getFirst());
        long vanId = fleet.vans().getFirst().getId();
        long planId = api.read(api.post(dispatcher, "/api/plans", Map.of("name", "Late", "serviceDate",
                LocalDate.now().toString(), "depotId", fleet.depot().getId()))).get("id").asLong();
        StringBuilder csv = new StringBuilder("order_ref,customer,address,lat,lon,window_start,window_end,size\n");
        ChicagoDayGenerator.generate(21, 14, 2).orders().forEach(o -> csv.append("%s,%s,\"%s\",%s,%s,08:00,18:00,%s%n"
                .formatted(o.orderRef(), o.customerName(), o.address(), o.point().lat(), o.point().lon(), o.size())));
        mvc.perform(multipart("/api/plans/" + planId + "/orders/import")
                .file(new MockMultipartFile("file", "o.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + dispatcher)).andExpect(status().isOk());
        api.post(dispatcher, "/api/plans/" + planId + "/optimize", null).andExpect(status().isAccepted());
        api.awaitJson(dispatcher, "/api/plans/" + planId, p -> p.get("status").asText().equals("PLANNED"), 30_000);
        // 600x: one real second is ten plan minutes, so reporting "now" makes the driver hours late.
        api.post(dispatcher, "/api/plans/" + planId + "/dispatch", Map.of("timeScale", 600)).andExpect(status().isOk());
        Thread.sleep(6_000);

        JsonNode route = api.read(api.get(driver, "/api/driver/route").andExpect(status().isOk()));
        List<Long> before = new ArrayList<>();
        route.get("stops").forEach(s -> before.add(s.get("id").asLong()));
        long first = before.get(0);
        String firstPlannedArrival = route.at("/stops/0/plannedArrival").asText();
        String departedAt = route.get("departDepot").asText();
        long second = before.get(1);
        report(driver, first, "DELIVERED");
        report(driver, second, "EN_ROUTE");

        JsonNode replanned = api.awaitJson(dispatcher, "/api/plans/" + planId,
                p -> p.get("version").asInt() >= 2 && !p.get("optimizing").asBoolean(), 30_000);
        JsonNode revisions = api.read(api.get(dispatcher, "/api/plans/" + planId + "/revisions"));
        assertThat(revisions.get(0).get("reason").asText()).isEqualTo("DRIVER_LATE");
        assertThat(revisions.get(0).get("detail").asText()).contains("min late");
        assertThat(revisions.get(0).get("pinnedStops").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode vanRoute = null;
        for (JsonNode r : replanned.get("routes")) {
            if (r.get("vehicleId").asLong() == vanId) {
                vanRoute = r;
            }
        }
        assertThat(vanRoute.get("departDepot").asText()).as("a van that has left keeps its departure").isEqualTo(departedAt);
        assertThat(vanRoute.at("/stops/0/id").asLong()).isEqualTo(first);
        assertThat(vanRoute.at("/stops/0/status").asText()).isEqualTo("DELIVERED");
        // History is kept: the plan still shows when the stop was planned, next to when it really happened.
        assertThat(vanRoute.at("/stops/0/plannedArrival").asText()).isEqualTo(firstPlannedArrival);
        assertThat(vanRoute.at("/stops/0/actualArrival").asText()).isNotEqualTo(firstPlannedArrival);
        assertThat(vanRoute.at("/stops/1/id").asLong()).isEqualTo(second);
        assertThat(vanRoute.at("/stops/1/status").asText()).isEqualTo("EN_ROUTE");
        assertThat(replanned.at("/totals/assigned").asInt()).isEqualTo(14);
    }

    private void report(String driver, long stopId, String type) throws Exception {
        api.post(driver, "/api/driver/stops/" + stopId + "/events",
                Map.of("eventId", UUID.randomUUID().toString(), "type", type, "occurredAt", Instant.now().toString()))
                .andExpect(status().isOk());
    }
}
