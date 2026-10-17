package com.barberia.api.barbers.dto;

import com.barberia.api.barbers.entity.BarberProfile;
import java.util.UUID;

public record BarberResponse(UUID id, String name, BranchSummary branch) {
    public record BranchSummary(UUID id, String name) { }
    public static BarberResponse from(BarberProfile profile) {
        return new BarberResponse(profile.getId(), profile.getAccount().getName(),
                new BranchSummary(profile.getBranch().getId(), profile.getBranch().getName()));
    }
}
