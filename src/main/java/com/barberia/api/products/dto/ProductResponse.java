package com.barberia.api.products.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, String description, BigDecimal price,
                              String currency, boolean active, BigInteger totalStock, List<BranchStock> branches) {
    public record BranchStock(UUID branchId, String branchName, boolean branchActive, long quantity) { }
}
