package com.routedispatch.tracking;

import com.routedispatch.security.CurrentUser;
import com.routedispatch.tracking.TrackingDtos.IngestResult;
import com.routedispatch.tracking.TrackingDtos.PingRequest;
import com.routedispatch.tracking.TrackingDtos.VehiclePositionView;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hot path for GPS pings, written with plain SQL. Each ping is stored once (its client id is the primary key,
 * so retried uploads are dropped), and the van's latest position only moves forward in time, so pings that
 * arrive out of order never drag the marker backwards.
 */
@Service
public class PositionIngestService {

    /** Device clocks drift; anything further in the future than this is rejected. */
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private static final String INSERT_PING = """
            INSERT INTO gps_ping (ping_id, plan_id, vehicle_id, recorded_at, location, speed_mps, heading_deg)
            VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
            ON CONFLICT (ping_id) DO NOTHING""";

    private static final String UPSERT_POSITION = """
            INSERT INTO vehicle_position (plan_id, vehicle_id, recorded_at, location, speed_mps, heading_deg)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
            ON CONFLICT (plan_id, vehicle_id) DO UPDATE
               SET recorded_at = EXCLUDED.recorded_at, location = EXCLUDED.location,
                   speed_mps = EXCLUDED.speed_mps, heading_deg = EXCLUDED.heading_deg
             WHERE vehicle_position.recorded_at < EXCLUDED.recorded_at""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ActivePlanResolver activePlans;
    private final PositionPublisher publisher;
    private final Clock clock;

    public PositionIngestService(JdbcTemplate jdbc, TransactionTemplate tx, ActivePlanResolver activePlans,
                                 PositionPublisher publisher, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.activePlans = activePlans;
        this.publisher = publisher;
        this.clock = clock;
    }

    public IngestResult ingest(CurrentUser driver, List<PingRequest> pings) {
        Instant limit = clock.instant().plus(MAX_CLOCK_SKEW);
        for (PingRequest ping : pings) {
            if (ping.recordedAt().isAfter(limit)) {
                throw new IllegalArgumentException("Ping " + ping.pingId() + " is timestamped in the future");
            }
        }
        long vehicleId = driver.vehicleId();
        long planId = activePlans.activePlanFor(vehicleId);
        PingRequest newest = pings.stream().max(Comparator.comparing(PingRequest::recordedAt)).orElseThrow();

        IngestResult result = tx.execute(status -> {
            int[] inserted = jdbc.batchUpdate(INSERT_PING, pings, pings.size(), (ps, ping) -> {
                ps.setObject(1, ping.pingId());
                ps.setLong(2, planId);
                ps.setLong(3, vehicleId);
                ps.setTimestamp(4, Timestamp.from(ping.recordedAt()));
                ps.setDouble(5, ping.lon());
                ps.setDouble(6, ping.lat());
                ps.setObject(7, ping.speedMps());
                ps.setObject(8, ping.headingDeg());
            })[0];
            int accepted = 0;
            for (int count : inserted) {
                accepted += count > 0 ? 1 : 0;
            }
            int moved = jdbc.update(UPSERT_POSITION, planId, vehicleId, Timestamp.from(newest.recordedAt()), newest.lon(),
                    newest.lat(), newest.speedMps(), newest.headingDeg());
            return new IngestResult(accepted, pings.size() - accepted, moved > 0);
        });
        if (result.positionUpdated()) {
            publisher.positionUpdated(planId, new VehiclePositionView(vehicleId, newest.lat(), newest.lon(),
                    newest.speedMps(), newest.headingDeg(), newest.recordedAt()));
        }
        return result;
    }
}
