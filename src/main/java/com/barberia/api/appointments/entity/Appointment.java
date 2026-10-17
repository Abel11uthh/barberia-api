package com.barberia.api.appointments.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID clientId;
    @Column(nullable = false, updatable = false) private UUID barberId;
    @Column(nullable = false, updatable = false) private UUID branchId;
    @Column(nullable = false, updatable = false) private Instant startsAt;
    @Column(nullable = false, updatable = false) private Instant endsAt;
    @Column(nullable = false, length = 16) private String status;
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
        total = services.stream().map(BookedService::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public UUID getId() { return id; }
    public UUID getBarberId() { return barberId; }
    public UUID getBranchId() { return branchId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public List<BookedService> getServices() { return List.copyOf(services); }
}
