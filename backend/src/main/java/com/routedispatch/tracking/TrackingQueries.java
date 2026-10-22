package com.routedispatch.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routedispatch.tracking.TrackingDtos.NearbyVehicleView;
import com.routedispatch.tracking.TrackingDtos.TrailView;
import com.routedispatch.tracking.TrackingDtos.VehiclePositionView;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostGIS queries over live positions and ping history. */
@Repository
public class TrackingQueries {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TrackingQueries(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<VehiclePositionView> latestPositions(long planId) {
        return jdbc.query("""
                SELECT vehicle_id, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon,
                       speed_mps, heading_deg, recorded_at
                  FROM vehicle_position WHERE plan_id = ? ORDER BY vehicle_id""",
                (rs, i) -> new VehiclePositionView(rs.getLong("vehicle_id"), rs.getDouble("lat"), rs.getDouble("lon"),
                        (Float) rs.getObject("speed_mps"), (Float) rs.getObject("heading_deg"),
                        rs.getTimestamp("recorded_at").toInstant()),
                planId);
    }

    /** Vans closest to a point right now, using the GiST index on the geography column (KNN operator). */
    public List<NearbyVehicleView> nearestVehicles(long planId, double lat, double lon, int limit) {
        return jdbc.query("""
                WITH target AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS g)
                SELECT vehicle_id, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon, recorded_at,
                       ST_Distance(location, target.g) AS meters
                  FROM vehicle_position, target
                 WHERE plan_id = ?
                 ORDER BY location <-> target.g
                 LIMIT ?""",
                (rs, i) -> new NearbyVehicleView(rs.getLong("vehicle_id"), rs.getDouble("lat"), rs.getDouble("lon"),
                        rs.getTimestamp("recorded_at").toInstant(), Math.round(rs.getDouble("meters") * 10) / 10.0),
                lon, lat, planId, limit);
    }

    /** The path a van actually drove: its pings since {@code since}, joined in recorded order. */
    public TrailView trail(long planId, long vehicleId, Instant since) {
        String geoJson = jdbc.queryForObject("""
                SELECT ST_AsGeoJSON(ST_RemoveRepeatedPoints(ST_MakeLine(location::geometry ORDER BY recorded_at)))
                  FROM gps_ping WHERE plan_id = ? AND vehicle_id = ? AND recorded_at >= ?""",
                String.class, planId, vehicleId, Timestamp.from(since));
        List<double[]> path = new ArrayList<>();
        if (geoJson != null) {
            try {
                JsonNode node = json.readTree(geoJson);
                JsonNode coordinates = "Point".equals(node.path("type").asText())
                        ? json.createArrayNode().add(node.get("coordinates")) : node.get("coordinates");
                coordinates.forEach(c -> path.add(new double[] {c.get(1).asDouble(), c.get(0).asDouble()}));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new TrailView(vehicleId, path);
    }
}
