package com.routedispatch.routing;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.IntStream;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to a self-hosted OSRM server. Large matrices are split into blocks of at most {@code chunkSize} origins by
 * {@code chunkSize} destinations, which keeps each request under OSRM's table size limit.
 */
public class OsrmTravelTimeProvider implements TravelTimeProvider {

    private final RestClient client;
    private final int chunkSize;

    public OsrmTravelTimeProvider(RestClient client, int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.client = client;
        this.chunkSize = chunkSize;
    }

    @Override
    public TravelMatrix matrix(List<GeoPoint> origins, List<GeoPoint> destinations) {
        long[][] seconds = new long[origins.size()][destinations.size()];
        long[][] meters = new long[origins.size()][destinations.size()];
        for (int oStart = 0; oStart < origins.size(); oStart += chunkSize) {
            List<GeoPoint> oChunk = origins.subList(oStart, Math.min(origins.size(), oStart + chunkSize));
            for (int dStart = 0; dStart < destinations.size(); dStart += chunkSize) {
                List<GeoPoint> dChunk = destinations.subList(dStart, Math.min(destinations.size(), dStart + chunkSize));
                fillBlock(oChunk, dChunk, oStart, dStart, seconds, meters);
            }
        }
        return new TravelMatrix(seconds, meters);
    }

    private void fillBlock(List<GeoPoint> oChunk, List<GeoPoint> dChunk, int oOffset, int dOffset,
                           long[][] seconds, long[][] meters) {
        List<GeoPoint> coordinates = new ArrayList<>(oChunk);
        coordinates.addAll(dChunk);
        String uri = "/table/v1/driving/" + coordinateList(coordinates)
                + "?sources=" + indexList(0, oChunk.size())
                + "&destinations=" + indexList(oChunk.size(), coordinates.size())
                + "&annotations=duration,distance";
        JsonNode body = get(uri);
        JsonNode durations = body.path("durations");
        JsonNode distances = body.path("distances");
        for (int i = 0; i < oChunk.size(); i++) {
            for (int j = 0; j < dChunk.size(); j++) {
                JsonNode duration = durations.path(i).path(j);
                JsonNode distance = distances.path(i).path(j);
                boolean unreachable = duration.isNull() || duration.isMissingNode() || distance.isNull() || distance.isMissingNode();
                seconds[oOffset + i][dOffset + j] = unreachable ? TravelMatrix.UNREACHABLE_SECONDS : Math.round(duration.asDouble());
                meters[oOffset + i][dOffset + j] = unreachable ? TravelMatrix.UNREACHABLE_METERS : Math.round(distance.asDouble());
            }
        }
    }

    @Override
    public RouteGeometry route(List<GeoPoint> waypoints) {
        if (waypoints.size() < 2) {
            return new RouteGeometry(List.of());
        }
        JsonNode body = get("/route/v1/driving/" + coordinateList(waypoints) + "?overview=false&steps=true&geometries=geojson");
        JsonNode legsNode = body.path("routes").path(0).path("legs");
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < legsNode.size(); i++) {
            JsonNode leg = legsNode.get(i);
            List<GeoPoint> path = new ArrayList<>();
            path.add(waypoints.get(i));
            for (JsonNode step : leg.path("steps")) {
                for (JsonNode coordinate : step.path("geometry").path("coordinates")) {
                    GeoPoint point = new GeoPoint(coordinate.get(1).asDouble(), coordinate.get(0).asDouble());
                    if (!path.getLast().equals(point)) {
                        path.add(point);
                    }
                }
            }
            // OSRM snaps waypoints to the road; end each leg on the exact stop coordinate for continuity.
            if (!path.getLast().equals(waypoints.get(i + 1))) {
                path.add(waypoints.get(i + 1));
            }
            legs.add(new Leg(path, Math.round(leg.path("distance").asDouble()), Math.round(leg.path("duration").asDouble())));
        }
        return new RouteGeometry(legs);
    }

    @Override
    public String name() {
        return "osrm";
    }

    private JsonNode get(String uri) {
        JsonNode body;
        try {
            body = client.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new RoutingException("OSRM request failed: " + e.getMessage(), e);
        }
        if (body == null || !"Ok".equals(body.path("code").asText())) {
            String code = body == null ? "empty response" : body.path("code").asText() + " " + body.path("message").asText("");
            throw new RoutingException("OSRM returned " + code.trim());
        }
        return body;
    }

    private static String coordinateList(List<GeoPoint> points) {
        StringJoiner joiner = new StringJoiner(";");
        for (GeoPoint p : points) {
            joiner.add(format(p.lon()) + "," + format(p.lat()));
        }
        return joiner.toString();
    }

    private static String format(double degrees) {
        return BigDecimal.valueOf(degrees).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String indexList(int fromInclusive, int toExclusive) {
        StringJoiner joiner = new StringJoiner(";");
        IntStream.range(fromInclusive, toExclusive).forEach(i -> joiner.add(Integer.toString(i)));
        return joiner.toString();
    }
}
