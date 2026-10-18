package com.barberia.api.sales.service;

import com.barberia.api.products.entity.*;
import com.barberia.api.products.repository.*;
import com.barberia.api.sales.dto.*;
import com.barberia.api.sales.entity.*;
import com.barberia.api.sales.repository.*;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.security.StaffAccess;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    private final SaleRepository sales;
    private final SaleOperationRepository operations;
    private final ProductRepository products;
    private final ProductStockRepository stocks;
    private final InventoryMovementRepository movements;
    private final StaffAccess access;
    private final Clock clock;
    public SaleService(SaleRepository sales, SaleOperationRepository operations, ProductRepository products,
            ProductStockRepository stocks, InventoryMovementRepository movements, StaffAccess access, Clock clock) {
        this.sales=sales; this.operations=operations; this.products=products; this.stocks=stocks;
        this.movements=movements; this.access=access; this.clock=clock;
    }
    public record Result(SaleResponse sale, boolean replayed) { }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public Result create(UUID actor, UUID key, SaleRequest request) {
        var branch=access.branch(actor,request.branchId(),false);
        var items=request.items().stream().sorted(Comparator.comparing(item -> item.productId().toString())).toList();
        if (items.stream().map(SaleRequest.Item::productId).distinct().count()!=items.size())
            throw new IllegalArgumentException("Productos repetidos.");
        String fingerprint=fingerprint(branch.getId(),request,items);
        operations.lock(actor,key);
        var existing=sales.findByActorIdAndIdempotencyKey(actor,key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint))
                throw new BusinessConflictException("La clave de idempotencia ya fue usada con otro contenido.");
            return new Result(SaleResponse.from(existing.get()),true);
        }
        if (!branch.isActive()) throw new BusinessConflictException("La sucursal está inactiva.");
        var lines=new ArrayList<SaleLine>();
        var balances=new ArrayList<ProductStock>();
        // The same total order is used by every sale, regardless of request item order.
        for (var item:items) {
            var product=products.findForUpdate(item.productId()).orElseThrow(NotFoundException::new);
            if (!product.isActive()) throw new BusinessConflictException("El producto está inactivo.");
            var stock=stocks.findById(new ProductStockId(product.getId(),branch.getId()))
                    .orElseThrow(() -> new BusinessConflictException("Existencias insuficientes."));
            if (stock.getQuantity()<item.quantity()) throw new BusinessConflictException("Existencias insuficientes.");
            lines.add(new SaleLine(product,item.quantity())); balances.add(stock);
        }
        var sale=new ProductSale(branch.getId(),actor,key,fingerprint,request.method(),lines,clock.instant());
        sales.saveAndFlush(sale);
        for (int index=0;index<lines.size();index++) {
            var line=lines.get(index); var stock=balances.get(index);
            long before=stock.getQuantity(); long after=before-line.getQuantity();
            stock.setQuantity(after); stocks.saveAndFlush(stock);
            movements.saveAndFlush(new InventoryMovement(line.getProductId(),branch.getId(),actor,"SALE",
                    -line.getQuantity(),before,after,"Venta presencial",sale.getPaidAt(),sale.getId()));
        }
        return new Result(SaleResponse.from(sale),false);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public SaleResponse detail(UUID actor, UUID id) {
        var sale=sales.findById(id).orElseThrow(NotFoundException::new);
        access.branch(actor,sale.getBranchId(),false);
        return SaleResponse.from(sale);
    }
    private String fingerprint(UUID branchId, SaleRequest request, List<SaleRequest.Item> items) {
        StringBuilder canonical=new StringBuilder(branchId.toString()).append('|').append(request.method().name());
        for (var item:items) canonical.append('|').append(item.productId()).append(':').append(item.quantity());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
