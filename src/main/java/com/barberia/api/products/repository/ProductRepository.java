package com.barberia.api.products.repository;

import com.barberia.api.products.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query("select p from Product p where (:includeInactive = true or p.active = true) and " +
            "(:search is null or lower(p.name) like lower(concat('%', :search, '%')) escape '!' " +
            "or lower(p.sku) like lower(concat('%', :search, '%')) escape '!')")
    Page<Product> search(boolean includeInactive, String search, Pageable pageable);
    Optional<Product> findByIdAndActiveTrue(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(UUID id);
    boolean existsBySku(String sku);
}
