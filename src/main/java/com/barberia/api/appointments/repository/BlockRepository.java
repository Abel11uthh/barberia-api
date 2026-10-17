package com.barberia.api.appointments.repository;
import com.barberia.api.appointments.entity.BarberBlock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface BlockRepository extends JpaRepository<BarberBlock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BarberBlock b where b.id=:id")
    Optional<BarberBlock> findForUpdate(UUID id);
    @Query("select b from BarberBlock b where (:barberId is null or b.barberId=:barberId) and b.startsAt<:end and b.endsAt>:start")
    Page<BarberBlock> agenda(UUID barberId, Instant start, Instant end, Pageable pageable);
}
