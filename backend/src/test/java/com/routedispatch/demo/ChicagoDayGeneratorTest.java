package com.routedispatch.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChicagoDayGeneratorTest {

    @Test
    void sameSeedProducesTheSameDay() {
        GeneratedDay first = ChicagoDayGenerator.generate(42, 200, 8);
        GeneratedDay second = ChicagoDayGenerator.generate(42, 200, 8);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentSeedProducesADifferentDay() {
        assertThat(ChicagoDayGenerator.generate(1, 50, 4).orders())
                .isNotEqualTo(ChicagoDayGenerator.generate(2, 50, 4).orders());
    }

    @Test
    void generatesTheRequestedFleetAndOrders() {
        GeneratedDay day = ChicagoDayGenerator.generate(42, 200, 8);

        assertThat(day.orders()).hasSize(200);
        assertThat(day.vehicles()).hasSize(8);
        assertThat(day.depot().address()).endsWith("Chicago, IL 60612");
    }

    @Test
    void ordersAreInsideChicagoWithValidWindowsAndUniqueRefs() {
        GeneratedDay day = ChicagoDayGenerator.generate(42, 200, 8);
        Set<String> refs = new HashSet<>();

        for (GeneratedOrder order : day.orders()) {
            assertThat(order.point().lat()).isBetween(41.64, 42.03);
            assertThat(order.point().lon()).isBetween(-87.94, -87.52);
            assertThat(order.windowEnd()).isAfter(order.windowStart());
            assertThat(order.windowStart()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(order.windowEnd()).isBeforeOrEqualTo(LocalTime.of(17, 0));
            assertThat(order.address()).matches("\\d+ [NSEW] [A-Za-z0-9 ]+, Chicago, IL");
            assertThat(refs.add(order.orderRef())).isTrue();
        }
    }

    @Test
    void fleetCapacityCoversTheDayWithHeadroom() {
        GeneratedDay day = ChicagoDayGenerator.generate(42, 200, 8);

        int demand = day.orders().stream().mapToInt(o -> o.size().units()).sum();
        int capacity = day.vehicles().stream().mapToInt(GeneratedVehicle::capacity).sum();
        assertThat(demand).isLessThan(capacity);
    }
}
