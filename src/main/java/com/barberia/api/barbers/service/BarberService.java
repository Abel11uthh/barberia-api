package com.barberia.api.barbers.service;

import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.barbers.dto.*;
import com.barberia.api.barbers.entity.BarberProfile;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.service.BranchService;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BarberService {
    private final BarberRepository barbers;
    private final BranchService branches;
    private final AccountService accounts;
    public BarberService(BarberRepository barbers, BranchService branches, AccountService accounts) {
        this.barbers = barbers; this.branches = branches; this.accounts = accounts;
    }
    public PageResponse<BarberResponse> list(UUID branchId, int page, int size, String sort) {
        return PageResponse.from(barbers.findAvailable(branchId, paging(page, size, sort)).map(BarberResponse::from));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminBarberResponse> listAll(UUID branchId, int page, int size, String sort) {
        return PageResponse.from(barbers.findCatalog(branchId, paging(page, size, sort)).map(AdminBarberResponse::from));
    }
    public BarberResponse get(UUID id) { return BarberResponse.from(barbers.findAvailableById(id).orElseThrow(NotFoundException::new)); }
    @PreAuthorize("hasRole('ADMIN')")
    public AdminBarberResponse getAny(UUID id) { return AdminBarberResponse.from(barbers.findById(id).orElseThrow(NotFoundException::new)); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminBarberResponse create(CreateBarberRequest request) {
        // El bloqueo de sucursal serializa la asignación con su desactivación.
        var branch = branches.activeForAssignment(request.branchId());
        var account = accounts.createBarberAccount(request.account());
        return save(new BarberProfile(account, branch));
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminBarberResponse assign(AssignBarberRequest request) {
        var branch = branches.activeForAssignment(request.branchId());
        // Serializa asignaciones simultáneas, incluso hacia sucursales distintas.
        var account = accounts.lockForBarberAssignment(request.accountId());
        if (barbers.existsByAccountId(account.getId())) {
            throw new BusinessConflictException("La cuenta ya tiene un perfil de barbero.");
        }
        account.addBarberRole();
        return save(new BarberProfile(account, branch));
    }
    private AdminBarberResponse save(BarberProfile profile) {
        try {
            return AdminBarberResponse.from(barbers.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException("No se pudo asignar el perfil de barbero.");
        }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(UUID id) { barbers.findForUpdate(id).orElseThrow(NotFoundException::new).deactivate(); }
    private org.springframework.data.domain.Pageable paging(int page, int size, String sort) {
        return CatalogPage.of(page, size, sort, Map.of("id","id","name","account.name"));
    }
}
