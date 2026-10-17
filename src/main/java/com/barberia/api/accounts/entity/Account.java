package com.barberia.api.accounts.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private int appointmentIncidentCount;
    @Column(nullable = false)
    private boolean commercialBlocked;
    @Column(nullable = false)
    private long appointmentEventSequence;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @ElementCollection
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    protected Account() { }

    public Account(String name, String phone, String email, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = true;
        this.createdAt = Instant.now();
        this.roles.addAll(roles);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public int getAppointmentIncidentCount() { return appointmentIncidentCount; }
    public boolean isCommercialBlocked() { return commercialBlocked; }
    public long nextAppointmentEventSequence() { return ++appointmentEventSequence; }
    public void addAppointmentIncident() {
        appointmentIncidentCount++;
        if (appointmentIncidentCount >= 3) commercialBlocked = true;
    }
    public void resetAppointmentIncidents() { appointmentIncidentCount = 0; }
    public void reactivateCommercialAccess() { appointmentIncidentCount = 0; commercialBlocked = false; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
    public void addBarberRole() { roles.add(Role.BARBER); }
}
