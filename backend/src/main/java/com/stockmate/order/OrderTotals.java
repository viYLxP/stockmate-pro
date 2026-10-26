package com.stockmate.order;

import java.math.BigDecimal;
import java.util.Collection;

import com.stockmate.common.Money;

/**
 * Subtotal, VAT and total of an order.
 * المجموع الفرعي والضريبة والإجمالي للطلب.
 *
 * <p>VAT is computed once on the subtotal (not per line) and rounded to halalas, which is how
 * Saudi tax invoices are normally totalled.</p>
 * <p>الضريبة تُحسب مرة وحدة على المجموع الفرعي (مو لكل سطر) وتُقرّب للهللة، وهذي الطريقة المعتادة
 * في إجماليات الفواتير الضريبية السعودية.</p>
 */
public record OrderTotals(BigDecimal subtotal, BigDecimal vat, BigDecimal total) {

    /**
     * @param lineTotals already-rounded line totals / إجماليات الأسطر بعد التقريب
     * @param vatRate    e.g. 0.15 for 15% / مثل 0.15 تعني 15%
     */
    public static OrderTotals of(Collection<BigDecimal> lineTotals, BigDecimal vatRate) {
        BigDecimal subtotal = Money.round(lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal vat = Money.round(subtotal.multiply(vatRate));
        return new OrderTotals(subtotal, vat, subtotal.add(vat));
    }
}
