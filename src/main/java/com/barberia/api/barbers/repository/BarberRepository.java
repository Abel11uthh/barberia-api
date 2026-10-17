package com.barberia.api.barbers.repository;

import com.barberia.api.barbers.entity.BarberProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BarberRepository extends JpaRepository<BarberProfile, UUID> {
    boolean existsByAccountId(UUID accountId);
    @EntityGraph(attributePaths = {"account", "branch"})
    @Query("select p from BarberProfile p where p.active = true and p.account.active = true and p.branch.active = true and (:branchId is null or p.branch.id = :branchId)")
    Page<BarberProfile> findAvailable(UUID branchId, Pageable pageable);
    @EntityGraph(attributePaths = {"account", "branch"})
    @Query("select p from BarberProfile p where (:branchId is null or p.branch.id = :branchId)")
    Page<BarberProfile> findCatalog(UUID branchId, Pageable pageable);
    @EntityGraph(attributePaths = {"account", "branch"})
    @Query("select p from BarberProfile p where p.id = :id and p.active = true and p.account.active = true and p.branch.active = true")
    Optional<BarberProfile> findAvailableById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BarberProfile p where p.id = :id")
    Optional<BarberProfile> findForUpdate(UUID id);
}
