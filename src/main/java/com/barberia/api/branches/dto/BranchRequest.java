package com.barberia.api.branches.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record BranchRequest(@NotBlank @Size(max = 120) String name,
                            @Size(max = 300) String address,
                            @NotNull @Size(min = 7, max = 7) List<@NotNull @Valid HoursRequest> weeklyHours) {
    public BranchRequest { name = name == null ? null : name.strip(); }
}
