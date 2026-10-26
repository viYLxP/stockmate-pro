package com.stockmate.order;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One order line as sent by the client (shared by purchase and sales orders).
 * سطر طلب واحد كما يرسله العميل (مشترك بين طلبات الشراء والبيع).
 *
 * @param unitPrice optional; when null we use the product's current cost (purchase) or sale price (sales)
 *                  / اختياري، ولو كان null نستخدم سعر التكلفة الحالي (شراء) أو سعر البيع (بيع)
 */
public record LineRequest(
        @NotNull Long productId,
        @Min(1) @Max(1_000_000) int quantity,
        @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
}
