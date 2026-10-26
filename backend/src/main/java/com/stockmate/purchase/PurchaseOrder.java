package com.stockmate.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.stockmate.order.OrderTotals;
import com.stockmate.partner.Supplier;

/**
 * A purchase order sent to a supplier.
 * طلب شراء مرسل لمورد.
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-friendly number like "PO-000042", generated from a database sequence (never reused).
    // رقم سهل القراءة مثل "PO-000042"، مولّد من sequence في قاعدة البيانات (لا يتكرر أبدًا).
    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.DRAFT;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    // cascade ALL + orphanRemoval: lines are saved with the order, and a line removed from this list is deleted.
    // cascade ALL و orphanRemoval: الأسطر تنحفظ مع الطلب، وأي سطر ينشال من القائمة ينحذف من قاعدة البيانات.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String number, Supplier supplier, String createdBy) {
        this.number = number;
        this.supplier = supplier;
        this.createdBy = createdBy;
    }

    /**
     * Replaces all lines and recomputes the totals in one step, so totals can never be stale.
     * يستبدل كل الأسطر ويعيد حساب الإجماليات بخطوة وحدة، فما يمكن تكون الإجماليات قديمة.
     */
    public void replaceLines(List<PurchaseOrderLine> newLines, BigDecimal vatRate) {
        lines.clear();
        newLines.forEach(line -> {
            line.attachTo(this);
            lines.add(line);
        });
        OrderTotals totals = OrderTotals.of(lines.stream().map(PurchaseOrderLine::getLineTotal).toList(), vatRate);
        subtotal = totals.subtotal();
        vat = totals.vat();
        total = totals.total();
    }

    public void markReceived() {
        status = PurchaseStatus.RECEIVED;
        receivedAt = Instant.now();
    }

    public void markCancelled() {
        status = PurchaseStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getVat() {
        return vat;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}
