package com.barberia.api.branches.entity;

import com.barberia.api.branches.dto.BranchRequest;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "branches")
public class Branch {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 300) private String address;
    @Column(nullable = false) private boolean active;
    @ElementCollection
    @CollectionTable(name = "branch_hours", joinColumns = @JoinColumn(name = "branch_id"))
    private List<BusinessHours> weeklyHours = new ArrayList<>();

    protected Branch() { }
    public Branch(BranchRequest request) { id = UUID.randomUUID(); active = true; update(request); }
    public void update(BranchRequest request) {
        name = request.name(); address = request.address();
        weeklyHours.clear();
        request.weeklyHours().forEach(day -> weeklyHours.add(new BusinessHours(day)));
    }
    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public List<BusinessHours> getWeeklyHours() { return List.copyOf(weeklyHours); }
}
