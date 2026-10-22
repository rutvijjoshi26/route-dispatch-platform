package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.routedispatch.demo.ChicagoDayGenerator;
import com.routedispatch.demo.GeneratedOrder;
import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PlanFlowIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;
    @Autowired MockMvc mvc;

    TestFleet.Fleet fleet;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        fleet = testFleet.create(3, 40);
        token = api.login(fleet.dispatcherEmail());
    }

    static String csv(List<GeneratedOrder> orders) {
        StringBuilder csv = new StringBuilder("order_ref,customer,address,lat,lon,window_start,window_end,size\n");
        for (GeneratedOrder o : orders) {
            csv.append("%s,%s,\"%s\",%s,%s,%s,%s,%s%n".formatted(o.orderRef(), o.customerName(), o.address(),
                    o.point().lat(), o.point().lon(), o.windowStart(), o.windowEnd(), o.size()));
        }
        return csv.toString();
    }

    long createPlan() throws Exception {
        JsonNode plan = api.read(api.post(token, "/api/plans",
                Map.of("name", "IT day", "serviceDate", LocalDate.now().toString(), "depotId", fleet.depot().getId()))
                .andExpect(status().isCreated()));
        return plan.get("id").asLong();
    }

    void importOrders(long planId, String csv) throws Exception {
        mvc.perform(multipart("/api/plans/" + planId + "/orders/import")
                        .file(new MockMultipartFile("file", "orders.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").isNumber());
    }

    @Test
    void dispatcherPlansDispatchesAndInsertsAnOrderMidShift() throws Exception {
        long planId = createPlan();
        importOrders(planId, csv(ChicagoDayGenerator.generate(3, 30, 3).orders()));

        api.post(token, "/api/plans/" + planId + "/optimize", null).andExpect(status().isAccepted());
        JsonNode planned = api.awaitJson(token, "/api/plans/" + planId,
                p -> p.get("status").asText().equals("PLANNED") && !p.get("optimizing").asBoolean(), 30_000);

        assertThat(planned.get("version").asInt()).isEqualTo(1);
        assertThat(planned.at("/totals/assigned").asInt()).isEqualTo(30);
        assertThat(planned.get("unassigned")).isEmpty();
        Set<Long> seen = new HashSet<>();
        for (JsonNode route : planned.get("routes")) {
            if (!route.get("stops").isEmpty()) {
                assertThat(route.get("path").size()).isGreaterThan(route.get("stops").size());
            }
            route.get("stops").forEach(s -> {
                assertThat(s.get("plannedArrival").isNull()).isFalse();
                assertThat(seen.add(s.get("id").asLong())).isTrue();
            });
        }

        JsonNode dispatched = api.read(api.post(token, "/api/plans/" + planId + "/dispatch", Map.of("timeScale", 60))
                .andExpect(status().isOk()));
        assertThat(dispatched.get("status").asText()).isEqualTo("DISPATCHED");
        assertThat(dispatched.at("/clock/timeScale").asDouble()).isEqualTo(60.0);
        assertThat(dispatched.at("/routes/0/stops/0/committedEta").isNull()).isFalse();

        api.post(token, "/api/plans/" + planId + "/orders", Map.of("orderRef", "LATE-1", "customerName", "New Customer",
                        "address", "2400 N Western Ave, Chicago, IL", "lat", 41.9254, "lon", -87.6874,
                        "windowStart", "09:00", "windowEnd", "17:00", "size", "MEDIUM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addedMidShift").value(true));

        JsonNode replanned = api.awaitJson(token, "/api/plans/" + planId,
                p -> p.get("version").asInt() == 2 && !p.get("optimizing").asBoolean(), 30_000);
        assertThat(replanned.get("unassigned")).isEmpty();
        assertThat(replanned.at("/totals/assigned").asInt()).isEqualTo(31);

        JsonNode revisions = api.read(api.get(token, "/api/plans/" + planId + "/revisions").andExpect(status().isOk()));
        assertThat(revisions.get(0).get("reason").asText()).isEqualTo("NEW_ORDER");
        assertThat(revisions.get(0).get("totalMillis").asLong()).isPositive();
        assertThat(revisions.get(1).get("reason").asText()).isEqualTo("INITIAL");
    }

    @Test
    void importingIntoAPlannedDayReplansSoTheNewOrdersGetRoutes() throws Exception {
        long planId = createPlan();
        List<GeneratedOrder> orders = ChicagoDayGenerator.generate(4, 12, 3).orders();
        importOrders(planId, csv(orders.subList(0, 8)));
        api.post(token, "/api/plans/" + planId + "/optimize", null).andExpect(status().isAccepted());
        api.awaitJson(token, "/api/plans/" + planId, p -> p.get("status").asText().equals("PLANNED"), 30_000);

        importOrders(planId, csv(orders.subList(8, 12)));

        JsonNode replanned = api.awaitJson(token, "/api/plans/" + planId,
                p -> p.get("version").asInt() == 2 && !p.get("optimizing").asBoolean(), 30_000);
        assertThat(replanned.at("/totals/assigned").asInt()).isEqualTo(12);
    }

    @Test
    void invalidCsvIsRejectedWithRowErrorsAndImportsNothing() throws Exception {
        long planId = createPlan();
        String csv = "order_ref,customer,address,lat,lon,window_start,window_end,size\n"
                + "A-1,Ana,1 N State St,41.88,-87.63,09:00,10:00,S\n"
                + "A-2,Ben,1 N State St,40.71,-74.00,09:00,10:00,S\n";

        mvc.perform(multipart("/api/plans/" + planId + "/orders/import")
                        .file(new MockMultipartFile("file", "orders.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order file"))
                .andExpect(jsonPath("$.errors[0].row").value(3));

        api.get(token, "/api/plans/" + planId).andExpect(jsonPath("$.totals.stops").value(0));
    }

    @Test
    void orderValidationErrorsNameTheFields() throws Exception {
        long planId = createPlan();

        api.post(token, "/api/plans/" + planId + "/orders", Map.of("orderRef", "", "customerName", "X", "address", "Y",
                        "lat", 41.9, "lon", -87.7, "windowStart", "12:00", "windowEnd", "10:00", "size", "SMALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'orderRef')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'windowValid')]").exists());
    }

    @Test
    void duplicateOrderRefIsAConflict() throws Exception {
        long planId = createPlan();
        Map<String, Object> order = Map.of("orderRef", "DUP-1", "customerName", "X", "address", "Y", "lat", 41.9,
                "lon", -87.7, "windowStart", "09:00", "windowEnd", "10:00", "size", "SMALL");

        api.post(token, "/api/plans/" + planId + "/orders", order).andExpect(status().isCreated());
        api.post(token, "/api/plans/" + planId + "/orders", order).andExpect(status().isConflict());
    }

    @Test
    void missingPlanIsNotFound() throws Exception {
        api.get(token, "/api/plans/999999").andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void dispatchingAnUnplannedDayIsAConflict() throws Exception {
        long planId = createPlan();

        api.post(token, "/api/plans/" + planId + "/dispatch", Map.of("timeScale", 10)).andExpect(status().isConflict());
    }
}
