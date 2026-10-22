package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.routing.GeoPoint;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StopStateMachineTest {

    static Stop pendingStop() {
        return Stop.newOrder(null, "CHI-1", "Maria Garcia", "1 N State St, Chicago, IL", new GeoPoint(41.88, -87.63),
                LocalTime.of(9, 0), LocalTime.of(12, 0), PackageSize.SMALL, false);
    }

    @Test
    void forwardTransitionsAreAppliedAndRecordTimes() {
        Stop stop = pendingStop();

        assertThat(stop.apply(StopEventType.EN_ROUTE, LocalTime.of(9, 10))).isEqualTo(EventOutcome.APPLIED);
        assertThat(stop.apply(StopEventType.ARRIVED, LocalTime.of(9, 25))).isEqualTo(EventOutcome.APPLIED);
        assertThat(stop.apply(StopEventType.DELIVERED, LocalTime.of(9, 29))).isEqualTo(EventOutcome.APPLIED);

        assertThat(stop.getStatus()).isEqualTo(StopStatus.DELIVERED);
        assertThat(stop.getEnRouteAt()).isEqualTo(LocalTime.of(9, 10));
        assertThat(stop.getActualArrival()).isEqualTo(LocalTime.of(9, 25));
        assertThat(stop.getActualDeparture()).isEqualTo(LocalTime.of(9, 29));
    }

    @Test
    void skippingArrivedStillRecordsAnArrivalTime() {
        Stop stop = pendingStop();

        stop.apply(StopEventType.DELIVERED, LocalTime.of(10, 0));

        assertThat(stop.getStatus()).isEqualTo(StopStatus.DELIVERED);
        assertThat(stop.getActualArrival()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void lateArrivingEarlierEventDoesNotMoveTheStopBackwards() {
        Stop stop = pendingStop();
        stop.apply(StopEventType.DELIVERED, LocalTime.of(10, 0));

        EventOutcome outcome = stop.apply(StopEventType.EN_ROUTE, LocalTime.of(9, 40));

        assertThat(outcome).isEqualTo(EventOutcome.IGNORED_STALE);
        assertThat(stop.getStatus()).isEqualTo(StopStatus.DELIVERED);
        assertThat(stop.getEnRouteAt()).isNull();
    }

    @Test
    void repeatedEventOfTheSameKindIsIgnored() {
        Stop stop = pendingStop();
        stop.apply(StopEventType.ARRIVED, LocalTime.of(9, 25));

        assertThat(stop.apply(StopEventType.ARRIVED, LocalTime.of(9, 27))).isEqualTo(EventOutcome.IGNORED_STALE);
        assertThat(stop.getActualArrival()).isEqualTo(LocalTime.of(9, 25));
    }

    @Test
    void terminalStatusIsFinal() {
        Stop stop = pendingStop();
        stop.apply(StopEventType.FAILED, LocalTime.of(9, 30));

        assertThat(stop.apply(StopEventType.DELIVERED, LocalTime.of(9, 35))).isEqualTo(EventOutcome.IGNORED_STALE);
        assertThat(stop.getStatus()).isEqualTo(StopStatus.FAILED);
    }

    @Test
    void onlyPendingStopsAreReplannable() {
        Stop stop = pendingStop();
        assertThat(stop.isPending()).isTrue();

        stop.apply(StopEventType.EN_ROUTE, LocalTime.of(9, 0));

        assertThat(stop.isPending()).isFalse();
        assertThat(stop.getStatus().isTerminal()).isFalse();
    }
}
