package com.barberia.api.accounts.dto;

import com.barberia.api.accounts.entity.Account;
import com.barberia.api.accounts.entity.Role;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String name, String phone, String email, Set<Role> roles) {
    public static UserResponse from(Account account) {
        return new UserResponse(account.getId(), account.getName(), account.getPhone(),
                account.getEmail(), account.getRoles());
    }
}
