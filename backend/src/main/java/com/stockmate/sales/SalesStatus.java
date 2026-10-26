package com.stockmate.sales;

/**
 * Sales order life cycle: DRAFT → CONFIRMED → CANCELLED, or DRAFT → CANCELLED.
 * دورة حياة طلب البيع: مسودة ← مؤكد ← ملغي، أو مسودة ← ملغي.
 *
 * <p>Confirming takes the goods out of stock. Cancelling a confirmed order puts them back (SALE_RETURN).</p>
 * <p>التأكيد يُخرج البضاعة من المخزون، وإلغاء طلب مؤكد يرجّعها (SALE_RETURN).</p>
 */
public enum SalesStatus {
    DRAFT,
    CONFIRMED,
    CANCELLED
}
