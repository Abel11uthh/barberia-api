package com.barberia.api.branches.dto;

import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.entity.BusinessHours;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record BranchResponse(UUID id, String name, String address, boolean active, String timeZone,
                             List<HoursRequest> weeklyHours) {
    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.isActive(),
                "America/Mexico_City", branch.getWeeklyHours().stream()
                .sorted(Comparator.comparing(BusinessHours::getDay)).map(BusinessHours::toResponse).toList());
    }
}
