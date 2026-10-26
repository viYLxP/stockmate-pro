package com.stockmate.purchase;

/**
 * Purchase order life cycle: DRAFT → RECEIVED, or DRAFT → CANCELLED.
 * دورة حياة طلب الشراء: مسودة ← مستلم، أو مسودة ← ملغي.
 *
 * <p>RECEIVED and CANCELLED are final: received goods are corrected with stock adjustments, not by
 * reopening the order.</p>
 * <p>المستلم والملغي حالات نهائية: البضاعة المستلمة تُصحح بتسويات مخزون، مو بإعادة فتح الطلب.</p>
 */
public enum PurchaseStatus {
    DRAFT,
    RECEIVED,
    CANCELLED
}
