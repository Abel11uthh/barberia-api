package com.barberia.api.appointments.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id private UUID id;
    @Column(updatable = false) private UUID clientId;
    @Column(nullable = false, updatable = false) private UUID barberId;
    @Column(nullable = false, updatable = false) private UUID branchId;
    @Column(nullable = false, updatable = false) private Instant startsAt;
    @Column(nullable = false, updatable = false) private Instant endsAt;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false, length = 16, updatable = false) private String origin;
    @Column(nullable = false, updatable = false) private UUID createdBy;
    @Column(length = 120, updatable = false) private String guestName;
    @Column(length = 32, updatable = false) private String guestPhone;
    private Instant startedAt;
    private Instant completedAt;
    private Instant noShowAt;
    private Instant cancelledAt;
    private UUID cancelledBy;
    @Column(length = 500) private String cancellationReason;
    @Column(length = 8) private String cancellationOrigin;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal total;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @ElementCollection
    @CollectionTable(name = "appointment_services", joinColumns = @JoinColumn(name = "appointment_id"))
    @OrderColumn(name = "position")
    private List<BookedService> services = new ArrayList<>();

    protected Appointment() { }
    public Appointment(UUID clientId, UUID barberId, UUID branchId, Instant start,
                       List<BookedService> services, Instant now) {
        id = UUID.randomUUID(); this.clientId = clientId; this.barberId = barberId;
        this.branchId = branchId; startsAt = start; endsAt = start.plusSeconds(3600);
        status = "CONFIRMED"; this.services.addAll(services); createdAt = now;
        origin = "RESERVATION"; createdBy = clientId;
        total = services.stream().map(BookedService::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public UUID getId() { return id; }
    public static Appointment walkIn(UUID barberId, UUID branchId, Instant start, List<BookedService> services,
                                     UUID actor, String name, String phone, Instant now) {
        var appointment = new Appointment(null, barberId, branchId, start, services, now);
        appointment.origin="WALK_IN"; appointment.createdBy=actor;
        appointment.guestName=name; appointment.guestPhone=phone;
        appointment.begin(now);
        return appointment;
    }
    public void begin(Instant now) { status="IN_PROGRESS"; startedAt=now; }
    public void complete(Instant now) { status="COMPLETED"; completedAt=now; }
    public void noShow(Instant now) { status="NO_SHOW"; noShowAt=now; }
    public void cancel(UUID actor, Instant now, String reason, String source) {
        status="CANCELLED"; cancelledBy=actor; cancelledAt=now; cancellationReason=reason; cancellationOrigin=source;
    }
    public UUID getClientId() { return clientId; }
    public String getOrigin() { return origin; }
    public UUID getCreatedBy() { return createdBy; }
    public String getGuestName() { return guestName; }
    public String getGuestPhone() { return guestPhone; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNoShowAt() { return noShowAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
    public String getCancellationOrigin() { return cancellationOrigin; }
    public UUID getBarberId() { return barberId; }
    public UUID getBranchId() { return branchId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public List<BookedService> getServices() { return List.copyOf(services); }
}
