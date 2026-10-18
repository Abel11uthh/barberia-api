package com.barberia.api.products.repository;

import com.barberia.api.products.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockRepository extends JpaRepository<ProductStock, ProductStockId> {
    List<ProductStock> findByProductId(UUID productId);
}
