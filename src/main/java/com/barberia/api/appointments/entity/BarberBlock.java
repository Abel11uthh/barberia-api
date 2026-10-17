package com.barberia.api.appointments.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "barber_blocks")
public class BarberBlock {
    @Id private UUID id;
    @Column(nullable=false, updatable=false) private UUID barberId;
    @Column(nullable=false, updatable=false) private Instant startsAt;
    @Column(nullable=false, updatable=false) private Instant endsAt;
    @Column(nullable=false, updatable=false, length=500) private String reason;
    @Column(nullable=false, updatable=false) private UUID createdBy;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false, length=12) private String status;
    private UUID cancelledBy;
    private Instant cancelledAt;
    protected BarberBlock() { }
    public BarberBlock(UUID barberId, Instant startsAt, Instant endsAt, String reason, UUID actor, Instant now) {
        id=UUID.randomUUID(); this.barberId=barberId; this.startsAt=startsAt; this.endsAt=endsAt;
        this.reason=reason; createdBy=actor; createdAt=now; status="ACTIVE";
    }
    public void cancel(UUID actor, Instant now) { status="CANCELLED"; cancelledBy=actor; cancelledAt=now; }
    public UUID getId() { return id; }
    public UUID getBarberId() { return barberId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public UUID getCancelledBy() { return cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
}
