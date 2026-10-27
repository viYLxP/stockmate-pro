package com.stockmate.sales;

/**
 * Delivery progress of an online order: NEW (being prepared) → SHIPPED → DELIVERED.
 * مراحل توصيل الطلب الإلكتروني: NEW (قيد التجهيز) ← SHIPPED (انشحن) ← DELIVERED (توصّل).
 */
public enum Fulfilment {
    NEW,
    SHIPPED,
    DELIVERED
}
