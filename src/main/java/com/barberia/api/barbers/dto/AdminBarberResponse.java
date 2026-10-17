package com.barberia.api.barbers.dto;

import com.barberia.api.barbers.entity.BarberProfile;
import java.util.UUID;

public record AdminBarberResponse(UUID id, String name, BarberResponse.BranchSummary branch,
                                 boolean active, boolean accountActive, boolean branchActive) {
    public static AdminBarberResponse from(BarberProfile profile) {
        var publicData = BarberResponse.from(profile);
        return new AdminBarberResponse(publicData.id(), publicData.name(), publicData.branch(),
                profile.isActive(), profile.getAccount().isActive(), profile.getBranch().isActive());
    }
}
