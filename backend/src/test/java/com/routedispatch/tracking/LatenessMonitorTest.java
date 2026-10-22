package com.routedispatch.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routedispatch.fleet.VehicleRepository;
import com.routedispatch.plan.DispatchPlanRepository;
import com.routedispatch.plan.PlanClock;
import com.routedispatch.plan.PlanRevision.Reason;
import com.routedispatch.plan.PlanService;
import com.routedispatch.plan.StopRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LatenessMonitorTest {

    final PlanService planService = mock(PlanService.class);
    final VehicleRepository vehicles = mock(VehicleRepository.class);
    final LatenessMonitor monitor = new LatenessMonitor(
            new LatenessProperties(Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofSeconds(5)),
            planService, mock(DispatchPlanRepository.class), mock(StopRepository.class), vehicles, mock(PlanClock.class));

    {
        when(vehicles.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void smallDelaysAreTolerated() {
        boolean triggered = monitor.reportDelay(1, 7, Duration.ofMinutes(10), LocalTime.of(10, 0));

        assertThat(triggered).isFalse();
        verify(planService, never()).requestReplan(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void driverRunningLateTriggersAReplanNamingTheDelay() {
        boolean triggered = monitor.reportDelay(1, 7, Duration.ofMinutes(25), LocalTime.of(10, 0));

        assertThat(triggered).isTrue();
        verify(planService).requestReplan(eq(1L), eq(Reason.DRIVER_LATE), contains("25 min late"));
    }

    @Test
    void replanThatIsNoLongerPossibleIsSkippedWithoutFailingOrUsingTheCooldown() {
        org.mockito.Mockito.doThrow(new com.routedispatch.common.ConflictException("plan completed"))
                .doNothing()
                .when(planService).requestReplan(eq(1L), eq(Reason.DRIVER_LATE), org.mockito.ArgumentMatchers.anyString());

        boolean first = monitor.reportDelay(1, 7, Duration.ofMinutes(25), LocalTime.of(16, 0));
        boolean retry = monitor.reportDelay(1, 7, Duration.ofMinutes(26), LocalTime.of(16, 1));

        assertThat(first).isFalse();
        assertThat(retry).isTrue();
    }

    @Test
    void sameVanIsNotReplannedAgainDuringTheCooldown() {
        monitor.reportDelay(1, 7, Duration.ofMinutes(25), LocalTime.of(10, 0));
        boolean again = monitor.reportDelay(1, 7, Duration.ofMinutes(30), LocalTime.of(10, 20));
        boolean otherVan = monitor.reportDelay(1, 8, Duration.ofMinutes(30), LocalTime.of(10, 20));
        boolean afterCooldown = monitor.reportDelay(1, 7, Duration.ofMinutes(30), LocalTime.of(10, 31));

        assertThat(again).isFalse();
        assertThat(otherVan).isTrue();
        assertThat(afterCooldown).isTrue();
        verify(planService, times(3)).requestReplan(eq(1L), eq(Reason.DRIVER_LATE), org.mockito.ArgumentMatchers.anyString());
    }
}
