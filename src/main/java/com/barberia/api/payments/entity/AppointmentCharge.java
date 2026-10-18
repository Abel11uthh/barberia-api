package com.barberia.api.payments.entity;

import com.barberia.api.shared.payment.PaymentMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="appointment_charges")
public class AppointmentCharge {
    @Id private UUID id;
    @Column(nullable=false, updatable=false, unique=true) private UUID appointmentId;
    @Column(nullable=false, updatable=false) private UUID branchId;
    @Column(nullable=false, updatable=false) private UUID actorId;
    @Column(nullable=false, updatable=false, precision=19, scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=4) private PaymentMethod method;
    @Column(nullable=false, updatable=false) private Instant paidAt;
    protected AppointmentCharge() { }
    public AppointmentCharge(UUID appointmentId, UUID branchId, UUID actorId, BigDecimal amount,
                              PaymentMethod method, Instant paidAt) {
        id=UUID.randomUUID(); this.appointmentId=appointmentId; this.branchId=branchId;
        this.actorId=actorId; this.amount=amount; this.method=method; this.paidAt=paidAt;
    }
    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getBranchId() { return branchId; }
    public UUID getActorId() { return actorId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public Instant getPaidAt() { return paidAt; }
}
