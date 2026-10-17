package com.barberia.api.accounts.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_client_events")
public class AppointmentClientEvent {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID accountId;
    @Column(nullable = false, updatable = false) private long sequence;
    @Column(nullable = false, length = 24, updatable = false) private String kind;
    @Column(updatable = false) private UUID appointmentId;
    @Column(updatable = false) private UUID actorId;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    @Column(length = 500, updatable = false) private String reason;
    @Column(nullable = false, updatable = false) private int resultingCount;
    @Column(nullable = false, updatable = false) private boolean resultingBlocked;

    protected AppointmentClientEvent() { }

    public AppointmentClientEvent(UUID accountId, long sequence, String kind, UUID appointmentId,
                                  UUID actorId, Instant occurredAt, String reason,
                                  int resultingCount, boolean resultingBlocked) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.sequence = sequence;
        this.kind = kind;
        this.appointmentId = appointmentId;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.reason = reason;
        this.resultingCount = resultingCount;
        this.resultingBlocked = resultingBlocked;
    }
}
