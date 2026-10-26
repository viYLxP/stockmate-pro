package com.stockmate.product;

/**
 * Stock level of a product.
 * مستوى مخزون المنتج.
 *
 * <p>OK: above the reorder level. LOW: at or below the reorder level. OUT: zero.</p>
 * <p>OK: أعلى من حد إعادة الطلب. LOW: عند الحد أو أقل. OUT: الكمية صفر.</p>
 */
public enum StockStatus {
    OK,
    LOW,
    OUT
}
