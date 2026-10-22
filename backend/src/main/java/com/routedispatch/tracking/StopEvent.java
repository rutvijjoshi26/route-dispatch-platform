package com.routedispatch.tracking;

import com.routedispatch.plan.EventOutcome;
import com.routedispatch.plan.StopEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Every driver event as received, keyed by the client-generated id so retries are recognized. */
@Entity
@Table(name = "stop_event")
public class StopEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Long stopId;

    @Column(nullable = false)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopEventType type;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventOutcome outcome;

    private String note;

    protected StopEvent() {
    }

    public StopEvent(UUID eventId, Long stopId, Long vehicleId, StopEventType type, Instant occurredAt,
                     EventOutcome outcome, String note) {
        this.eventId = eventId;
        this.stopId = stopId;
        this.vehicleId = vehicleId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.outcome = outcome;
        this.note = note;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getStopId() {
        return stopId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public StopEventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public EventOutcome getOutcome() {
        return outcome;
    }

    public String getNote() {
        return note;
    }
}
