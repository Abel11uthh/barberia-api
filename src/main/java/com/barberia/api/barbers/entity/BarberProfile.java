package com.barberia.api.barbers.entity;

import com.barberia.api.accounts.entity.Account;
import com.barberia.api.branches.entity.Branch;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "barber_profiles")
public class BarberProfile {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true, updatable = false)
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, updatable = false)
    private Branch branch;
    @Column(nullable = false) private boolean active;
    protected BarberProfile() { }
    public BarberProfile(Account account, Branch branch) {
        id = UUID.randomUUID(); this.account = account; this.branch = branch; active = true;
    }
    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public Account getAccount() { return account; }
    public Branch getBranch() { return branch; }
    public boolean isActive() { return active; }
}
