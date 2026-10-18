package com.barberia.api.products.service;

import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.products.dto.*;
import com.barberia.api.products.entity.*;
import com.barberia.api.products.repository.*;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.sql.SQLException;
import java.math.BigInteger;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductInventoryService {
    private static final Map<String, String> PRODUCT_SORT = Map.of(
            "id", "id", "sku", "sku", "name", "name", "price", "price");
    private static final Map<String, String> MOVEMENT_SORT = Map.of("id", "id", "occurredAt", "occurredAt");
    private final ProductRepository products;
    private final ProductStockRepository stocks;
    private final InventoryMovementRepository movements;
    private final BranchRepository branches;
    private final BarberRepository barbers;
    private final Clock clock;

    public ProductInventoryService(ProductRepository products, ProductStockRepository stocks,
            InventoryMovementRepository movements, BranchRepository branches, BarberRepository barbers, Clock clock) {
        this.products = products; this.stocks = stocks; this.movements = movements;
        this.branches = branches; this.barbers = barbers; this.clock = clock;
    }

    @PreAuthorize("hasRole('CLIENT')")
    public PageResponse<ProductResponse> listForClient(String search, int page, int size, String sort) {
        return list(false, search, page, size, sort, branches.findAll().stream().filter(Branch::isActive).toList());
    }
    @PreAuthorize("hasRole('CLIENT')")
    public ProductResponse getForClient(UUID id) {
        return response(products.findByIdAndActiveTrue(id).orElseThrow(NotFoundException::new),
                branches.findAll().stream().filter(Branch::isActive).toList());
    }
    @PreAuthorize("hasRole('BARBER')")
    public PageResponse<ProductResponse> listForBarber(UUID actor, String search, int page, int size, String sort) {
        return list(false, search, page, size, sort, List.of(ownBranch(actor)));
    }
    @PreAuthorize("hasRole('BARBER')")
    public ProductResponse getForBarber(UUID actor, UUID id) {
        return response(products.findByIdAndActiveTrue(id).orElseThrow(NotFoundException::new), List.of(ownBranch(actor)));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ProductResponse> listForAdmin(String search, int page, int size, String sort) {
        return list(true, search, page, size, sort, branches.findAll());
    }
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse getForAdmin(UUID id) {
        return response(products.findById(id).orElseThrow(NotFoundException::new), branches.findAll());
    }
    private PageResponse<ProductResponse> list(boolean includeInactive, String search, int page, int size,
                                                String sort, List<Branch> visibleBranches) {
        Pageable paging = CatalogPage.of(page, size, sort, PRODUCT_SORT);
        String query = normalizeSearch(search);
        return PageResponse.from(products.search(includeInactive, query, paging)
                .map(product -> response(product, visibleBranches)));
    }
    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String trimmed = search.strip();
        if (trimmed.length() > 120)
            throw new IllegalArgumentException("Búsqueda inválida.");
        return trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
    private Branch ownBranch(UUID actor) {
        return barbers.findByAccountId(actor).orElseThrow(NotFoundException::new).getBranch();
    }
    private ProductResponse response(Product product, List<Branch> visibleBranches) {
        var quantities = new HashMap<UUID, Long>();
        for (var stock : stocks.findByProductId(product.getId())) {
            // The composite identifier is used only for lookup inside this module.
            quantities.put(stock.getBranchId(), stock.getQuantity());
        }
        var branchStocks = visibleBranches.stream().sorted(Comparator.comparing(Branch::getName).thenComparing(Branch::getId))
                .map(branch -> new ProductResponse.BranchStock(branch.getId(), branch.getName(),
                        branch.isActive(), quantities.getOrDefault(branch.getId(), 0L))).toList();
        BigInteger total = branchStocks.stream().map(stock -> BigInteger.valueOf(stock.quantity()))
                .reduce(BigInteger.ZERO, BigInteger::add);
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), "MXN", product.isActive(), total, branchStocks);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request);
        if (products.existsBySku(product.getSku())) throw duplicateSku();
        try { products.saveAndFlush(product); }
        catch (DataIntegrityViolationException error) { throw translateSkuConflict(error); }
        return response(product, branches.findAll());
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = products.findForUpdate(id).orElseThrow(NotFoundException::new);
        String requestedSku = request.sku().strip().toUpperCase(Locale.ROOT);
        // Query before modifying the managed entity: an exists query can trigger an automatic flush.
        if (!product.getSku().equals(requestedSku) && products.existsBySku(requestedSku)) throw duplicateSku();
        product.update(request);
        try { products.flush(); }
        catch (DataIntegrityViolationException error) { throw translateSkuConflict(error); }
        return response(product, branches.findAll());
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(UUID id) { products.findForUpdate(id).orElseThrow(NotFoundException::new).deactivate(); }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MovementResponse entry(UUID actor, UUID productId, MovementRequest request) {
        if (request.quantity() <= 0) throw new IllegalArgumentException("La entrada debe ser positiva.");
        return move(actor, productId, request, "ENTRY");
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MovementResponse adjustment(UUID actor, UUID productId, MovementRequest request) {
        if (request.quantity() == 0) throw new IllegalArgumentException("El ajuste no puede ser cero.");
        return move(actor, productId, request, "ADJUSTMENT");
    }
    private MovementResponse move(UUID actor, UUID productId, MovementRequest request, String kind) {
        // This row lock serializes creation and updates of stock rows, including the first entry.
        Product product = products.findForUpdate(productId).orElseThrow(NotFoundException::new);
        if (!product.isActive()) throw new BusinessConflictException("El producto está inactivo.");
        Branch branch = branches.findForBooking(request.branchId()).orElseThrow(NotFoundException::new);
        if (!branch.isActive()) throw new BusinessConflictException("La sucursal está inactiva.");
        ProductStockId stockId = new ProductStockId(productId, branch.getId());
        ProductStock stock = stocks.findById(stockId).orElseGet(() -> new ProductStock(productId, branch.getId()));
        long before = stock.getQuantity();
        long after;
        try { after = Math.addExact(before, request.quantity()); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Cantidad inválida."); }
        if (after < 0) throw new BusinessConflictException("Existencias insuficientes.");
        stock.setQuantity(after);
        stocks.saveAndFlush(stock);
        var movement = new InventoryMovement(productId, branch.getId(), actor, kind, request.quantity(),
                before, after, request.reason().strip(), clock.instant());
        movements.saveAndFlush(movement);
        return MovementResponse.from(movement);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<MovementResponse> history(UUID productId, UUID branchId, int page, int size, String sort) {
        if (!products.existsById(productId)) throw new NotFoundException();
        if (branchId != null && !branches.existsById(branchId)) throw new NotFoundException();
        Pageable paging = CatalogPage.of(page, size, sort, MOVEMENT_SORT);
        return PageResponse.from(movements.history(productId, branchId, paging).map(MovementResponse::from));
    }
    private BusinessConflictException duplicateSku() { return new BusinessConflictException("El SKU ya está registrado."); }
    private RuntimeException translateSkuConflict(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) return duplicateSku();
        return error;
    }
}
