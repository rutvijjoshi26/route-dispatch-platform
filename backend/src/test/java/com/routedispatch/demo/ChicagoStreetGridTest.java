package com.routedispatch.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.routedispatch.routing.GeoPoint;
import com.routedispatch.routing.HaversineTravelTimeProvider;
import org.junit.jupiter.api.Test;

class ChicagoStreetGridTest {

    @Test
    void gridOriginIsStateAndMadison() {
        GeoPoint origin = ChicagoStreetGrid.toPoint(0, 0);

        assertThat(origin.lat()).isCloseTo(41.8820, within(0.0005));
        assertThat(origin.lon()).isCloseTo(-87.6278, within(0.0005));
    }

    @Test
    void eightHundredAddressNumbersAreOneMile() {
        GeoPoint west = ChicagoStreetGrid.toPoint(-800, 0);
        GeoPoint north = ChicagoStreetGrid.toPoint(0, 800);

        double mile = 1609.34;
        assertThat(HaversineTravelTimeProvider.greatCircleMeters(ChicagoStreetGrid.toPoint(0, 0), west)).isCloseTo(mile, within(15.0));
        assertThat(HaversineTravelTimeProvider.greatCircleMeters(ChicagoStreetGrid.toPoint(0, 0), north)).isCloseTo(mile, within(15.0));
    }

    @Test
    void ashlandAndFullertonLandsNearTheRealIntersection() {
        // 1600 W Fullerton Ave (2400 N): real intersection is about 41.9254, -87.6680
        GeoPoint point = ChicagoStreetGrid.toPoint(-1600, 2400);

        assertThat(HaversineTravelTimeProvider.greatCircleMeters(point, new GeoPoint(41.9254, -87.6680))).isLessThan(250);
    }
}
