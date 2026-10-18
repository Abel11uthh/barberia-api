package com.barberia.api.sales.repository;
import com.barberia.api.sales.entity.ProductSale;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SaleRepository extends JpaRepository<ProductSale,UUID> {
    Optional<ProductSale> findByActorIdAndIdempotencyKey(UUID actorId, UUID key);
}
