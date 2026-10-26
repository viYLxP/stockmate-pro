package com.stockmate.purchase;

import java.math.BigDecimal;

import com.stockmate.product.Product;

/**
 * Exposes package-private helpers to unit tests in another package.
 * يكشف دوال داخلية (package-private) لاختبارات الوحدة الموجودة في حزمة ثانية.
 */
public final class PurchaseTestAccess {

    private PurchaseTestAccess() {
    }

    public static void updateAverageCost(Product product, int oldQty, int receivedQty, BigDecimal unitCost) {
        PurchaseService.updateAverageCost(product, oldQty, receivedQty, unitCost);
    }
}
