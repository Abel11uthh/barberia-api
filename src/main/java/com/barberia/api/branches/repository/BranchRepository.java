package com.barberia.api.branches.repository;

import com.barberia.api.branches.entity.Branch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> findForBooking(UUID id);
    Page<Branch> findByActiveTrue(Pageable pageable);
    Optional<Branch> findByIdAndActiveTrue(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> findForUpdate(UUID id);
}
