package com.stockmate.product;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stockmate.catalog.Category;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.partner.Supplier;

/**
 * A stock item. Its quantity changes ONLY through stock movements, never by editing the product directly.
 * صنف في المخزون. كميته تتغير فقط عن طريق حركات المخزون، ولا تتغير بتعديل المنتج مباشرة.
 *
 * <p>This rule guarantees that every unit in or out has an audit record explaining why.</p>
 * <p>هذه القاعدة تضمن إن كل وحدة دخلت أو طلعت لها سجل يوضح السبب.</p>
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stock Keeping Unit: the unique product code printed on labels and barcodes.
    // رمز الصنف (SKU): الكود الفريد المطبوع على الملصقات والباركود.
    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    // LAZY: do not load the category from the database until it is actually used.
    // LAZY: لا نحمّل التصنيف من قاعدة البيانات إلا عند استخدامه فعلًا.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Optional preferred supplier.
    // المورد المفضل، وهو اختياري.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    // Selling unit, e.g. "piece", "box", "kg".
    // وحدة البيع، مثل "حبة" أو "كرتون" أو "كجم".
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    // Current stock on hand. The database CHECK constraint also forbids negative values.
    // الكمية الحالية في المخزن. وقيد CHECK في قاعدة البيانات يمنع القيم السالبة أيضًا.
    @Column(nullable = false)
    private int quantity;

    // When quantity falls to this level or below, the product is flagged as "low stock".
    // لما تنزل الكمية لهذا الحد أو أقل، يظهر المنتج كـ "مخزون منخفض".
    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    // Archived products are hidden from new orders but stay in reports and history.
    // المنتج المؤرشف يختفي من الطلبات الجديدة لكنه يبقى في التقارير والسجل.
    @Column(nullable = false)
    private boolean active = true;

    // Optimistic locking: JPA increments it on every update and rejects stale writes.
    // القفل المتفائل: JPA يزيده مع كل تعديل ويرفض الحفظ لو كانت النسخة قديمة.
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, String name, Category category) {
        this.sku = sku;
        this.name = name;
        this.category = category;
    }

    /**
     * Applies a signed quantity change and returns the new balance.
     * يطبّق تغيير الكمية (موجب أو سالب) ويرجّع الرصيد الجديد.
     *
     * @param delta positive adds stock, negative removes it / الموجب يضيف والسالب ينقص
     * @throws ApiException INSUFFICIENT_STOCK if the result would be negative / إذا صار الرصيد سالبًا
     */
    public int changeQuantity(int delta) {
        int next = quantity + delta;
        // Never allow negative stock: you cannot sell what you do not have.
        // ما نسمح بمخزون سالب: ما تقدر تبيع شي ما عندك.
        if (next < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    java.util.Map.of("sku", sku, "available", quantity, "requested", -delta));
        }
        quantity = next;
        return quantity;
    }

    /**
     * Stock status derived from quantity and reorder level (not stored, so it can never be out of sync).
     * حالة المخزون محسوبة من الكمية وحد إعادة الطلب (غير مخزّنة، فلا يمكن تتعارض مع البيانات).
     */
    public StockStatus stockStatus() {
        if (quantity == 0) {
            return StockStatus.OUT;
        }
        return quantity <= reorderLevel ? StockStatus.LOW : StockStatus.OK;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
