package com.stockmate.sales;

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
import com.stockmate.partner.Customer;

/**
 * A sales order (invoice) for a customer.
 * طلب بيع (فاتورة) لعميل.
 */
@Entity
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. "SO-000017", from its own database sequence.
    // مثل "SO-000017"، من sequence خاص في قاعدة البيانات.
    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesStatus status = SalesStatus.DRAFT;

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

    // Set when the order is confirmed; sales reports use this date, not the creation date.
    // يتحدد عند تأكيد الطلب، وتقارير المبيعات تعتمد على هذا التاريخ مو تاريخ الإنشاء.
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SalesOrderLine> lines = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(String number, Customer customer, String createdBy) {
        this.number = number;
        this.customer = customer;
        this.createdBy = createdBy;
    }

    /**
     * Replaces all lines and recomputes the totals in one step.
     * يستبدل كل الأسطر ويعيد حساب الإجماليات بخطوة وحدة.
     */
    public void replaceLines(List<SalesOrderLine> newLines, BigDecimal vatRate) {
        lines.clear();
        newLines.forEach(line -> {
            line.attachTo(this);
            lines.add(line);
        });
        OrderTotals totals = OrderTotals.of(lines.stream().map(SalesOrderLine::getLineTotal).toList(), vatRate);
        subtotal = totals.subtotal();
        vat = totals.vat();
        total = totals.total();
    }

    public void markConfirmed() {
        status = SalesStatus.CONFIRMED;
        confirmedAt = Instant.now();
    }

    public void markCancelled() {
        status = SalesStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public SalesStatus getStatus() {
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public List<SalesOrderLine> getLines() {
        return lines;
    }
}
