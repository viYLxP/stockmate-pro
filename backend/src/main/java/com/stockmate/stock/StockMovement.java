package com.stockmate.stock;

import java.time.Instant;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.stockmate.product.Product;

/**
 * An immutable ledger entry: one change to a product's quantity.
 * قيد ثابت في دفتر المخزون: تغيير واحد في كمية منتج.
 *
 * <p>Movements are never edited or deleted. A mistake is fixed with a new opposite adjustment,
 * the same way accountants fix journal entries.</p>
 * <p>الحركات لا تُعدّل ولا تُحذف. الخطأ يُصحح بحركة تسوية معاكسة جديدة،
 * بنفس طريقة المحاسبين في تصحيح القيود.</p>
 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    // Signed change: +10 for receiving ten units, -3 for selling three.
    // التغيير بإشارة: ‎+10 عند استلام عشر حبات، و‎-3 عند بيع ثلاث.
    @Column(nullable = false)
    private int quantity;

    // Product quantity right after this movement, which makes a running-balance report trivial.
    // كمية المنتج مباشرة بعد هذه الحركة، وهذا يسهّل تقرير الرصيد التراكمي.
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    // Source document number (e.g. "PO-000012"), empty for manual adjustments.
    // رقم المستند المصدر (مثل "PO-000012")، وفاضي للتسويات اليدوية.
    @Column(length = 30)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, MovementType type, int quantity, int balanceAfter,
                         String reference, String note, String createdBy) {
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.reference = reference;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
