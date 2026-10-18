package com.barberia.api.sales.entity;
import com.barberia.api.shared.payment.PaymentMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity
@Table(name="product_sales")
public class ProductSale {
    @Id private UUID id;
    @Column(nullable=false, updatable=false) private UUID branchId;
    @Column(nullable=false, updatable=false) private UUID actorId;
    @Column(nullable=false, updatable=false) private UUID idempotencyKey;
    @Column(nullable=false, updatable=false, length=64) private String requestFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false, updatable=false, length=4) private PaymentMethod method;
    @Column(nullable=false, updatable=false, precision=38, scale=2) private BigDecimal total;
    @Column(nullable=false, updatable=false) private Instant paidAt;
    @ElementCollection
    @CollectionTable(name="product_sale_lines", joinColumns=@JoinColumn(name="sale_id"))
    @OrderColumn(name="position")
    private List<SaleLine> lines=new ArrayList<>();
    protected ProductSale() { }
    public ProductSale(UUID branchId, UUID actorId, UUID key, String fingerprint, PaymentMethod method,
                       List<SaleLine> lines, Instant now) {
        id=UUID.randomUUID(); this.branchId=branchId; this.actorId=actorId; idempotencyKey=key;
        requestFingerprint=fingerprint; this.method=method; this.lines.addAll(lines); paidAt=now;
        total=lines.stream().map(SaleLine::total).reduce(new BigDecimal("0.00"),BigDecimal::add);
    }
    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public UUID getActorId() { return actorId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getTotal() { return total; }
    public Instant getPaidAt() { return paidAt; }
    public List<SaleLine> getLines() { return List.copyOf(lines); }
}
