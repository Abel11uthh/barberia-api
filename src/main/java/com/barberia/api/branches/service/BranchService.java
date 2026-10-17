package com.barberia.api.branches.service;

import com.barberia.api.branches.dto.*;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BranchService {
    private final BranchRepository branches;
    public BranchService(BranchRepository branches) { this.branches = branches; }
    public PageResponse<BranchResponse> list(int page, int size, String sort) {
        return PageResponse.from(branches.findByActiveTrue(paging(page, size, sort)).map(BranchResponse::from));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<BranchResponse> listAll(int page, int size, String sort) {
        return PageResponse.from(branches.findAll(paging(page, size, sort)).map(BranchResponse::from));
    }
    public BranchResponse get(UUID id) { return BranchResponse.from(branches.findByIdAndActiveTrue(id).orElseThrow(NotFoundException::new)); }
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse getAny(UUID id) { return BranchResponse.from(branches.findById(id).orElseThrow(NotFoundException::new)); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse create(BranchRequest request) {
        WeeklyHoursRules.validate(request.weeklyHours());
        return BranchResponse.from(branches.save(new Branch(request)));
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponse update(UUID id, BranchRequest request) {
        WeeklyHoursRules.validate(request.weeklyHours());
        var branch = branches.findForUpdate(id).orElseThrow(NotFoundException::new);
        branch.update(request);
        return BranchResponse.from(branch);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(UUID id) { branches.findForUpdate(id).orElseThrow(NotFoundException::new).deactivate(); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Branch activeForAssignment(UUID id) {
        var branch = branches.findForUpdate(id).orElseThrow(NotFoundException::new);
        if (!branch.isActive()) { throw new BusinessConflictException("La sucursal está inactiva."); }
        return branch;
    }
    private org.springframework.data.domain.Pageable paging(int page, int size, String sort) {
        return CatalogPage.of(page, size, sort, Map.of("id", "id", "name", "name"));
    }
}
