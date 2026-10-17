package com.barberia.api.services.repository;

import com.barberia.api.services.entity.CatalogService;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ServiceRepository extends JpaRepository<CatalogService, UUID> {
    Page<CatalogService> findByActiveTrue(Pageable pageable);
    Optional<CatalogService> findByIdAndActiveTrue(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogService s where s.id = :id")
    Optional<CatalogService> findForUpdate(UUID id);
}
