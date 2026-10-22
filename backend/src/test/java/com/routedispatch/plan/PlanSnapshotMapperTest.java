package com.routedispatch.plan;

import static com.routedispatch.plan.PlanFixtures.assigned;
import static com.routedispatch.plan.PlanFixtures.stop;
import static com.routedispatch.plan.PlanFixtures.van;
import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.fleet.Vehicle;
import com.routedispatch.optimization.VehicleSpec;
import com.routedispatch.optimization.VisitSpec;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlanSnapshotMapperTest {

    final Vehicle van1 = van(1, LocalTime.of(8, 0), LocalTime.of(17, 0));
    final Vehicle van2 = van(2, LocalTime.of(9, 0), LocalTime.of(18, 0));
    final PlanSnapshotMapper mapper = new PlanSnapshotMapper();

    static Map<String, VisitSpec> byId(List<VisitSpec> visits) {
        return visits.stream().collect(Collectors.toMap(VisitSpec::id, Function.identity()));
    }

    @Test
    void beforeDispatchNothingIsPinnedAndCurrentRoutesAreTheWarmStart() {
        Stop a = assigned(10, 1, 1, LocalTime.of(9, 0));
        Stop b = assigned(11, 1, 0, LocalTime.of(8, 30));
        Stop unassigned = stop(12, LocalTime.of(9, 0), LocalTime.of(12, 0));

        PlanSnapshot snapshot = mapper.map(new PlanState(PlanFixtures.DEPOT.getLocation(), List.of(van1, van2),
                List.of(a, b, unassigned), Map.of(), null), PlanState.currentOrder(List.of(a, b, unassigned)));

        VehicleSpec first = snapshot.vehicles().getFirst();
        assertThat(first.visitIds()).containsExactly("11", "10");
        assertThat(first.pinnedCount()).isZero();
        assertThat(first.availableFrom()).isEqualTo(LocalTime.of(8, 0).toSecondOfDay());
        assertThat(snapshot.vehicles().get(1).visitIds()).isEmpty();
        assertThat(snapshot.visits()).hasSize(3);
        assertThat(snapshot.pinnedStops()).isZero();
        assertThat(byId(snapshot.visits()).get("12").windowEnd()).isEqualTo(LocalTime.of(12, 0).toSecondOfDay());
    }

    @Test
    void midShiftVisitedAndCurrentStopsArePinnedWithRealTimes() {
        Stop done = assigned(10, 1, 0, LocalTime.of(8, 20));
        done.commitEta();
        done.apply(StopEventType.ARRIVED, LocalTime.of(8, 25));
        done.apply(StopEventType.DELIVERED, LocalTime.of(8, 29));
        Stop heading = assigned(11, 1, 1, LocalTime.of(8, 45));
        heading.commitEta();
        heading.apply(StopEventType.EN_ROUTE, LocalTime.of(8, 35));
        Stop later = assigned(12, 1, 2, LocalTime.of(9, 10));
        later.commitEta();
        LocalTime now = LocalTime.of(8, 40);

        PlanSnapshot snapshot = mapper.map(new PlanState(PlanFixtures.DEPOT.getLocation(), List.of(van1),
                List.of(done, heading, later), Map.of(1L, LocalTime.of(8, 0)), now), PlanState.currentOrder(List.of(done, heading, later)));

        VehicleSpec vehicle = snapshot.vehicles().getFirst();
        assertThat(vehicle.visitIds()).containsExactly("10", "11", "12");
        assertThat(vehicle.pinnedCount()).isEqualTo(2);
        assertThat(vehicle.availableFrom()).isEqualTo(now.toSecondOfDay());
        Map<String, VisitSpec> visits = byId(snapshot.visits());
        assertThat(visits.get("10").fixedArrival()).isEqualTo(LocalTime.of(8, 25).toSecondOfDay());
        assertThat(visits.get("10").fixedDeparture()).isEqualTo(LocalTime.of(8, 29).toSecondOfDay());
        // planned leg 08:23 -> 08:45 is 22 minutes; the driver left at 08:35, so expected 08:57
        assertThat(visits.get("11").fixedArrival()).isEqualTo(LocalTime.of(8, 57).toSecondOfDay());
        assertThat(visits.get("12").fixedArrival()).isNull();
        assertThat(visits.get("12").committedEta()).isEqualTo(LocalTime.of(9, 10).toSecondOfDay());
        assertThat(snapshot.pinnedStops()).isEqualTo(2);
    }

    @Test
    void enRouteStopThatIsOverdueIsExpectedNow() {
        Stop heading = assigned(11, 1, 0, LocalTime.of(8, 20));
        heading.apply(StopEventType.EN_ROUTE, LocalTime.of(8, 0));
        LocalTime now = LocalTime.of(9, 0);

        PlanSnapshot snapshot = mapper.map(new PlanState(PlanFixtures.DEPOT.getLocation(), List.of(van1),
                List.of(heading), Map.of(1L, LocalTime.of(8, 0)), now), PlanState.currentOrder(List.of(heading)));

        assertThat(snapshot.visits().getFirst().fixedArrival()).isEqualTo(now.toSecondOfDay());
    }

    @Test
    void arrivedStopLeavesAfterServiceButNotBeforeNow() {
        Stop here = assigned(11, 1, 0, LocalTime.of(8, 20));
        here.apply(StopEventType.ARRIVED, LocalTime.of(8, 30));
        LocalTime now = LocalTime.of(8, 31);

        PlanSnapshot snapshot = mapper.map(new PlanState(PlanFixtures.DEPOT.getLocation(), List.of(van1),
                List.of(here), Map.of(1L, LocalTime.of(8, 0)), now), PlanState.currentOrder(List.of(here)));

        VisitSpec visit = snapshot.visits().getFirst();
        assertThat(visit.fixedArrival()).isEqualTo(LocalTime.of(8, 30).toSecondOfDay());
        assertThat(visit.fixedDeparture()).isEqualTo(LocalTime.of(9, 3).toSecondOfDay());
    }
}
