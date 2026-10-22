package com.routedispatch.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class HaversineTravelTimeProviderTest {

    static final GeoPoint WILLIS_TOWER = new GeoPoint(41.8789, -87.6359);
    static final GeoPoint WRIGLEY_FIELD = new GeoPoint(41.9484, -87.6553);

    final HaversineTravelTimeProvider provider = new HaversineTravelTimeProvider(1.35, 28.0);

    @Test
    void greatCircleDistanceMatchesKnownValue() {
        double meters = HaversineTravelTimeProvider.greatCircleMeters(WILLIS_TOWER, WRIGLEY_FIELD);
        assertThat(meters).isCloseTo(7_890, within(60.0));
    }

    @Test
    void matrixAppliesCircuityAndAverageSpeed() {
        TravelMatrix matrix = provider.matrix(List.of(WILLIS_TOWER, WRIGLEY_FIELD), List.of(WILLIS_TOWER, WRIGLEY_FIELD));

        long expectedMeters = Math.round(HaversineTravelTimeProvider.greatCircleMeters(WILLIS_TOWER, WRIGLEY_FIELD) * 1.35);
        assertThat(matrix.meters(0, 1)).isEqualTo(expectedMeters);
        assertThat(matrix.seconds(0, 1)).isEqualTo(Math.round(expectedMeters / (28.0 / 3.6)));
        assertThat(matrix.seconds(0, 0)).isZero();
        assertThat(matrix.meters(1, 1)).isZero();
    }

    @Test
    void routeLegsAreStraightLinesBetweenWaypoints() {
        GeoPoint third = new GeoPoint(41.85, -87.65);

        RouteGeometry route = provider.route(List.of(WILLIS_TOWER, WRIGLEY_FIELD, third));

        assertThat(route.legs()).hasSize(2);
        assertThat(route.legs().get(0).path()).containsExactly(WILLIS_TOWER, WRIGLEY_FIELD);
        assertThat(route.legs().get(1).path()).containsExactly(WRIGLEY_FIELD, third);
        assertThat(route.fullPath()).containsExactly(WILLIS_TOWER, WRIGLEY_FIELD, third);
    }
}
