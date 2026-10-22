package com.routedispatch.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds square travel matrices on top of a {@link TravelTimeProvider}, caching every origin/destination pair.
 *
 * <p>Fetch strategy for the pairs that are not cached:
 * <ol>
 *   <li>Points with no cached pairs at all (new stops) are fetched as one row block and one column block, so
 *       inserting a single order into a 200-stop day costs 1 x 201 + 200 x 1 pairs instead of 201 x 201.</li>
 *   <li>Any pairs still missing afterwards (for example after cache eviction) are fetched as the smallest
 *       rectangle of affected rows and columns.</li>
 * </ol>
 */
public class TravelMatrixService {

    private record PairKey(long from, long to) {
    }

    private final TravelTimeProvider provider;
    private final Cache<PairKey, long[]> cache;

    public TravelMatrixService(TravelTimeProvider provider, long maxCachedPairs) {
        this.provider = provider;
        this.cache = Caffeine.newBuilder().maximumSize(maxCachedPairs).build();
    }

    public TravelMatrix matrix(List<GeoPoint> points) {
        int n = points.size();
        long[][] seconds = new long[n][n];
        long[][] meters = new long[n][n];
        boolean[][] missing = new boolean[n][n];
        int missingCount = fillFromCache(points, seconds, meters, missing);
        if (missingCount == 0) {
            return new TravelMatrix(seconds, meters);
        }

        List<Integer> fresh = new ArrayList<>();
        List<Integer> known = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            (isEntirelyMissing(points, missing, i) ? fresh : known).add(i);
        }
        if (!fresh.isEmpty()) {
            List<Integer> all = range(n);
            fetch(points, fresh, all);
            if (!known.isEmpty()) {
                fetch(points, known, fresh);
            }
            missingCount = fillFromCache(points, seconds, meters, missing);
        }
        if (missingCount > 0) {
            List<Integer> rows = new ArrayList<>();
            List<Integer> cols = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                boolean rowMissing = false;
                for (int j = 0; j < n; j++) {
                    rowMissing |= missing[i][j];
                }
                if (rowMissing) {
                    rows.add(i);
                }
            }
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    if (missing[i][j]) {
                        cols.add(j);
                        break;
                    }
                }
            }
            fetch(points, rows, cols);
            fillFromCache(points, seconds, meters, missing);
        }
        return new TravelMatrix(seconds, meters);
    }

    public RouteGeometry route(List<GeoPoint> waypoints) {
        return provider.route(waypoints);
    }

    public String providerName() {
        return provider.name();
    }

    private int fillFromCache(List<GeoPoint> points, long[][] seconds, long[][] meters, boolean[][] missing) {
        int count = 0;
        for (int i = 0; i < points.size(); i++) {
            long from = points.get(i).cacheKey();
            for (int j = 0; j < points.size(); j++) {
                long to = points.get(j).cacheKey();
                if (from == to) {
                    seconds[i][j] = 0;
                    meters[i][j] = 0;
                    missing[i][j] = false;
                    continue;
                }
                long[] value = cache.getIfPresent(new PairKey(from, to));
                missing[i][j] = value == null;
                if (value == null) {
                    count++;
                } else {
                    seconds[i][j] = value[0];
                    meters[i][j] = value[1];
                }
            }
        }
        return count;
    }

    private static boolean isEntirelyMissing(List<GeoPoint> points, boolean[][] missing, int row) {
        long key = points.get(row).cacheKey();
        for (int j = 0; j < points.size(); j++) {
            if (points.get(j).cacheKey() != key && !missing[row][j]) {
                return false;
            }
        }
        return true;
    }

    private void fetch(List<GeoPoint> points, List<Integer> rows, List<Integer> cols) {
        List<GeoPoint> origins = rows.stream().map(points::get).toList();
        List<GeoPoint> destinations = cols.stream().map(points::get).toList();
        TravelMatrix block = provider.matrix(origins, destinations);
        for (int i = 0; i < origins.size(); i++) {
            long from = origins.get(i).cacheKey();
            for (int j = 0; j < destinations.size(); j++) {
                long to = destinations.get(j).cacheKey();
                if (from != to) {
                    cache.put(new PairKey(from, to), new long[] {block.seconds(i, j), block.meters(i, j)});
                }
            }
        }
    }

    private static List<Integer> range(int n) {
        List<Integer> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            all.add(i);
        }
        return all;
    }
}
