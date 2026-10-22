package com.routedispatch.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TravelMatrixServiceTest {

    /** Records every matrix request so the tests can assert on what the cache avoided. */
    static final class RecordingProvider implements TravelTimeProvider {
        final HaversineTravelTimeProvider delegate = new HaversineTravelTimeProvider(1.3, 30);
        final List<int[]> requests = new ArrayList<>();

        @Override
        public TravelMatrix matrix(List<GeoPoint> origins, List<GeoPoint> destinations) {
            requests.add(new int[] {origins.size(), destinations.size()});
            return delegate.matrix(origins, destinations);
        }

        @Override
        public RouteGeometry route(List<GeoPoint> waypoints) {
            return delegate.route(waypoints);
        }

        @Override
        public String name() {
            return "recording";
        }

        long pairsRequested() {
            return requests.stream().mapToLong(r -> (long) r[0] * r[1]).sum();
        }
    }

    static List<GeoPoint> grid(int n) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new GeoPoint(41.80 + i * 0.01, -87.70 + (i % 5) * 0.01));
        }
        return points;
    }

    final RecordingProvider provider = new RecordingProvider();
    final TravelMatrixService service = new TravelMatrixService(provider, 100_000);

    @Test
    void firstRequestFetchesTheFullMatrixOnce() {
        List<GeoPoint> points = grid(10);

        TravelMatrix matrix = service.matrix(points);

        assertThat(provider.requests).hasSize(1);
        assertThat(provider.pairsRequested()).isEqualTo(100);
        assertThat(matrix.seconds(3, 7)).isEqualTo(provider.delegate.matrix(List.of(points.get(3)), List.of(points.get(7))).seconds(0, 0));
    }

    @Test
    void repeatedRequestIsServedFromCache() {
        List<GeoPoint> points = grid(10);
        service.matrix(points);
        provider.requests.clear();

        TravelMatrix again = service.matrix(points);

        assertThat(provider.requests).isEmpty();
        assertThat(again.size()).isEqualTo(10);
    }

    @Test
    void addingOnePointOnlyFetchesItsRowAndColumn() {
        List<GeoPoint> points = new ArrayList<>(grid(10));
        service.matrix(points);
        provider.requests.clear();

        points.add(new GeoPoint(41.95, -87.66));
        TravelMatrix matrix = service.matrix(points);

        // one row (1 x 11) and one column (10 x 1) instead of 121 pairs
        assertThat(provider.pairsRequested()).isEqualTo(21);
        assertThat(matrix.size()).isEqualTo(11);
        assertThat(matrix.seconds(10, 10)).isZero();
        assertThat(matrix.seconds(2, 10)).isPositive();
    }

    @Test
    void reorderedSubsetIsServedFromCache() {
        List<GeoPoint> points = grid(6);
        service.matrix(points);
        provider.requests.clear();

        TravelMatrix subset = service.matrix(List.of(points.get(4), points.get(1)));

        assertThat(provider.requests).isEmpty();
        assertThat(subset.seconds(0, 1)).isEqualTo(service.matrix(points).seconds(4, 1));
    }
}
