package com.stockmate.product;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {
    }

    /**
     * Create request. {@code openingQuantity} is recorded as an OPENING stock movement, not written directly.
     * طلب الإنشاء. {@code openingQuantity} تُسجّل كحركة مخزون افتتاحية، ولا تُكتب مباشرة.
     */
    public record CreateProductRequest(
            // SKU: uppercase letters, digits and dashes (e.g. "BEV-001"), so it prints cleanly on barcodes.
            // رمز الصنف: حروف إنجليزية كبيرة وأرقام وشرطات (مثل "BEV-001") حتى يُطبع بوضوح على الباركود.
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$") String sku,
            @NotBlank @Size(max = 150) String name,
            @NotNull Long categoryId,
            Long supplierId,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
            @Min(0) @Max(1_000_000) int reorderLevel,
            @Min(0) @Max(1_000_000) int openingQuantity,
            @Valid ShopListing shop) {
    }

    /**
     * Update request. Quantity is intentionally absent: stock only changes through movements.
     * طلب التعديل. الكمية غير موجودة عمدًا: المخزون يتغير فقط عبر الحركات.
     *
     * @param version the version the user loaded; a mismatch means someone else saved first
     *                / النسخة اللي حمّلها المستخدم، ولو اختلفت يعني أحد ثاني حفظ قبله
     */
    public record UpdateProductRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$") String sku,
            @NotBlank @Size(max = 150) String name,
            @NotNull Long categoryId,
            Long supplierId,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
            @Min(0) @Max(1_000_000) int reorderLevel,
            boolean active,
            @NotNull Long version,
            @Valid ShopListing shop) {
    }

    /**
     * How the product appears in the online shop. Null (or published = false) keeps it office-only.
     * كيف يظهر المنتج في المتجر الإلكتروني. لو null (أو published = false) يبقى للمكتب بس.
     */
    public record ShopListing(boolean published,
                              @Size(max = 150) String nameAr,
                              @Size(max = 1000) String description,
                              @Size(max = 1000) String descriptionAr) {

        static ShopListing of(Product p) {
            return new ShopListing(p.isPublished(), p.getNameAr(), p.getDescription(), p.getDescriptionAr());
        }
    }

    public record ProductResponse(Long id, String sku, String name, Long categoryId, String categoryName,
                                  Long supplierId, String supplierName, String unit, BigDecimal costPrice,
                                  BigDecimal salePrice, int quantity, int reorderLevel, StockStatus stockStatus,
                                  BigDecimal stockValue, boolean active, long version, Instant updatedAt,
                                  ShopListing shop) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(),
                    p.getCategory().getId(), p.getCategory().getName(),
                    p.getSupplier() == null ? null : p.getSupplier().getId(),
                    p.getSupplier() == null ? null : p.getSupplier().getName(),
                    p.getUnit(), p.getCostPrice(), p.getSalePrice(), p.getQuantity(), p.getReorderLevel(),
                    p.stockStatus(),
                    // Value of this product's stock at cost.
                    // قيمة مخزون هذا المنتج بسعر التكلفة.
                    com.stockmate.common.Money.times(p.getCostPrice(), p.getQuantity()),
                    p.isActive(), p.getVersion(), p.getUpdatedAt(), ShopListing.of(p));
        }
    }
}
