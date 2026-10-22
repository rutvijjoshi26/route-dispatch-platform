package com.routedispatch.fleet;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routedispatch.support.Api;
import com.routedispatch.support.IntegrationTest;
import com.routedispatch.support.TestFleet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class FleetApiIT {

    @Autowired Api api;
    @Autowired TestFleet testFleet;

    @Test
    void dispatcherSeesDepotsAndVansWithShifts() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(2, 25);
        String token = api.login(fleet.dispatcherEmail());
        long depotId = fleet.depot().getId();
        long vanId = fleet.vans().getFirst().getId();

        api.get(token, "/api/fleet/depots").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + depotId + ")].lat").value(41.8866));
        api.get(token, "/api/fleet/vehicles").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vanId + ")].capacity").value(25))
                .andExpect(jsonPath("$[?(@.id == " + vanId + ")].depotId").value((int) depotId))
                .andExpect(jsonPath("$[?(@.id == " + vanId + ")].shiftEnd").value("18:00:00"));
    }

    @Test
    void driversCannotListTheFleet() throws Exception {
        TestFleet.Fleet fleet = testFleet.create(1, 25);

        api.get(api.login(fleet.driverEmails().getFirst()), "/api/fleet/vehicles").andExpect(status().isForbidden());
    }
}
