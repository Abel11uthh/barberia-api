package com.barberia.api.services.service;

import com.barberia.api.services.dto.*;
import com.barberia.api.services.entity.CatalogService;
import com.barberia.api.services.repository.ServiceRepository;
import com.barberia.api.shared.error.NotFoundException;
import com.barberia.api.shared.pagination.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceCatalogService {
    private final ServiceRepository services;
    public ServiceCatalogService(ServiceRepository services) { this.services = services; }
    public PageResponse<ServiceResponse> list(int page, int size, String sort) {
        return PageResponse.from(services.findByActiveTrue(paging(page, size, sort)).map(ServiceResponse::from));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ServiceResponse> listAll(int page, int size, String sort) {
        return PageResponse.from(services.findAll(paging(page, size, sort)).map(ServiceResponse::from));
    }
    public ServiceResponse get(UUID id) { return ServiceResponse.from(services.findByIdAndActiveTrue(id).orElseThrow(NotFoundException::new)); }
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse getAny(UUID id) { return ServiceResponse.from(services.findById(id).orElseThrow(NotFoundException::new)); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse create(ServiceRequest request) { return ServiceResponse.from(services.save(new CatalogService(request))); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceResponse update(UUID id, ServiceRequest request) {
        var service = services.findForUpdate(id).orElseThrow(NotFoundException::new);
        service.update(request);
        return ServiceResponse.from(service);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(UUID id) { services.findForUpdate(id).orElseThrow(NotFoundException::new).deactivate(); }
    private org.springframework.data.domain.Pageable paging(int page, int size, String sort) {
        return CatalogPage.of(page, size, sort, Map.of("id","id","name","name","price","price","type","type"));
    }
}
