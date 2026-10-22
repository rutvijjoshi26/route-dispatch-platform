package com.routedispatch.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.fleet.DepotRepository;
import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlan;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.StopRepository;
import com.routedispatch.security.AppUserRepository;
import com.routedispatch.security.Role;
import com.routedispatch.support.IntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
class DemoDataLoaderIT {

    @Autowired DepotRepository depots;
    @Autowired VehicleRepository vehicles;
    @Autowired AppUserRepository users;
    @Autowired DispatchPlanRepository plans;
    @Autowired StopRepository stops;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    @Test
    @Transactional
    void loadsTheSeededChicagoDayWithLogins() {
        DemoDataLoader loader = new DemoDataLoader(depots, vehicles, users, plans, stops, encoder, clock, "dispatch-pw", "driver-pw", 3);
        loader.load();
        loader.loadTestFleet();

        DispatchPlan plan = plans.findAll().stream().filter(p -> p.getName().equals("Chicago demo day")).findFirst().orElseThrow();
        assertThat(stops.countByPlanId(plan.getId())).isEqualTo(200);
        assertThat(vehicles.findByDepotIdOrderById(plan.getDepot().getId())).hasSize(8);
        var dispatcher = users.findByEmailIgnoreCase("dispatcher@demo.local").orElseThrow();
        assertThat(dispatcher.getRole()).isEqualTo(Role.DISPATCHER);
        assertThat(encoder.matches("dispatch-pw", dispatcher.getPasswordHash())).isTrue();
        assertThat(users.findByEmailIgnoreCase("driver8@demo.local").orElseThrow().getVehicleId()).isNotNull();
        assertThat(users.findByEmailIgnoreCase("load3@demo.local")).isPresent();
        assertThat(plans.findAll()).anySatisfy(p -> {
            assertThat(p.getName()).isEqualTo("Load test");
            assertThat(p.isLive()).isTrue();
        });
    }
}
