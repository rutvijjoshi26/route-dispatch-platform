package com.routedispatch.plan;

import static com.routedispatch.plan.PlanFixtures.assigned;
import static com.routedispatch.plan.PlanFixtures.stop;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteReconcilerTest {

    final RouteReconciler reconciler = new RouteReconciler();

    @Test
    void solutionIsUsedAsIsWhenNothingHappenedDuringTheSolve() {
        Stop a = assigned(1, 10, 0, LocalTime.of(9, 0));
        Stop b = assigned(2, 10, 1, LocalTime.of(9, 30));
        Stop c = stop(3, LocalTime.of(9, 0), LocalTime.of(12, 0));

        RouteReconciler.Result result = reconciler.reconcile(
                Map.of(10L, List.of(2L, 1L), 20L, List.of(3L)), List.of(a, b, c), List.of(10L, 20L));

        assertThat(result.orders()).containsEntry(10L, List.of(2L, 1L)).containsEntry(20L, List.of(3L));
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void stopTheDriverStartedDuringTheSolveStaysWithThatDriver() {
        Stop a = assigned(1, 10, 0, LocalTime.of(9, 0));
        Stop b = assigned(2, 10, 1, LocalTime.of(9, 30));
        // While the solver ran, van 10's driver set off to stop 2, but the solution moved it to van 20.
        b.apply(StopEventType.EN_ROUTE, LocalTime.of(9, 10));
        a.apply(StopEventType.DELIVERED, LocalTime.of(9, 5));

        RouteReconciler.Result result = reconciler.reconcile(
                Map.of(10L, List.of(1L), 20L, List.of(2L)), List.of(a, b), List.of(10L, 20L));

        assertThat(result.orders().get(10L)).containsExactly(1L, 2L);
        assertThat(result.orders().get(20L)).isEmpty();
    }

    @Test
    void everyStopEndsUpExactlyOnce() {
        Stop a = assigned(1, 10, 0, LocalTime.of(9, 0));
        Stop added = stop(2, LocalTime.of(9, 0), LocalTime.of(12, 0));
        Stop addedDuringSolve = stop(3, LocalTime.of(9, 0), LocalTime.of(12, 0));

        RouteReconciler.Result result = reconciler.reconcile(
                Map.of(10L, List.of(1L, 2L)), List.of(a, added, addedDuringSolve), List.of(10L));

        assertThat(result.orders().get(10L)).containsExactly(1L, 2L);
        assertThat(result.unassigned()).containsExactly(3L);
    }

    @Test
    void stopsMissingFromTheDatabaseAreDropped() {
        Stop a = assigned(1, 10, 0, LocalTime.of(9, 0));

        RouteReconciler.Result result = reconciler.reconcile(Map.of(10L, List.of(1L, 99L)), List.of(a), List.of(10L));

        assertThat(result.orders().get(10L)).containsExactly(1L);
    }
}
