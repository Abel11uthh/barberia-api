package com.barberia.api.sales.dto;
import com.barberia.api.sales.entity.ProductSale;
import com.barberia.api.shared.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record SaleResponse(UUID id, UUID branchId, UUID actorId, PaymentMethod method, Instant paidAt,
                           BigDecimal total, String currency, List<Line> items) {
    public record Line(UUID productId, String name, String sku, BigDecimal unitPrice, long quantity, BigDecimal total) { }
    public static SaleResponse from(ProductSale sale) {
        return new SaleResponse(sale.getId(),sale.getBranchId(),sale.getActorId(),sale.getMethod(),sale.getPaidAt(),
                sale.getTotal(),"MXN",sale.getLines().stream().map(line -> new Line(line.getProductId(),line.getName(),
                        line.getSku(),line.getUnitPrice(),line.getQuantity(),line.total())).toList());
    }
}
