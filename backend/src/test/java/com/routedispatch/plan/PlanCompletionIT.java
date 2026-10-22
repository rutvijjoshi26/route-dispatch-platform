package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PlanCompletionIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;
    @Autowired MockMvc mvc;

    @Test
    void dayCompletesWhenEveryRoutedStopIsFinishedEvenIfSomeOrdersCouldNotBeRouted() throws Exception {
        // One van with room for two small packages and three orders: one order cannot be routed.
        TestFleet.Fleet fleet = testFleet.create(1, 2);
        String dispatcher = api.login(fleet.dispatcherEmail());
        String driver = api.login(fleet.driverEmails().getFirst());
        long planId = api.read(api.post(dispatcher, "/api/plans", Map.of("name", "Completion", "serviceDate",
                LocalDate.now().toString(), "depotId", fleet.depot().getId()))).get("id").asLong();
        String csv = "order_ref,customer,address,lat,lon,window_start,window_end,size\n"
                + "C-1,Ana,1 N State St,41.89,-87.68,08:00,18:00,S\n"
                + "C-2,Ben,2 N State St,41.90,-87.68,08:00,18:00,S\n"
                + "C-3,Cy,3 N State St,41.91,-87.68,08:00,18:00,S\n";
        mvc.perform(multipart("/api/plans/" + planId + "/orders/import")
                .file(new MockMultipartFile("file", "o.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + dispatcher)).andExpect(status().isOk());
        api.post(dispatcher, "/api/plans/" + planId + "/optimize", null).andExpect(status().isAccepted());
        JsonNode planned = api.awaitJson(dispatcher, "/api/plans/" + planId,
                p -> p.get("status").asText().equals("PLANNED"), 30_000);
        assertThat(planned.get("unassigned")).hasSize(1);
        api.post(dispatcher, "/api/plans/" + planId + "/dispatch", Map.of("timeScale", 1)).andExpect(status().isOk());

        for (JsonNode stop : planned.at("/routes/0/stops")) {
            api.post(driver, "/api/driver/stops/" + stop.get("id").asLong() + "/events", Map.of("eventId",
                    UUID.randomUUID().toString(), "type", "DELIVERED", "occurredAt", Instant.now().toString()))
                    .andExpect(status().isOk());
        }

        assertThat(api.read(api.get(dispatcher, "/api/plans/" + planId)).get("status").asText()).isEqualTo("COMPLETED");
    }
}
