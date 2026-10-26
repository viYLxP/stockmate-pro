package com.stockmate.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.stockmate.catalog.Category;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.dashboard.DashboardTestAccess;
import com.stockmate.order.OrderTotals;
import com.stockmate.partner.Customer;
import com.stockmate.product.Product;
import com.stockmate.product.StockStatus;
import com.stockmate.purchase.PurchaseTestAccess;
import com.stockmate.sales.SalesOrder;

/**
 * Fast unit tests for pure business rules (no Spring, no database).
 * اختبارات وحدة سريعة لقواعد العمل البحتة (بدون Spring وبدون قاعدة بيانات).
 */
class DomainRulesTest {

    private static Product product(int quantity, int reorderLevel) {
        Product p = new Product("SKU-1", "Test", new Category("Cat", null));
        p.setCostPrice(new BigDecimal("10.00"));
        p.setReorderLevel(reorderLevel);
        if (quantity > 0) {
            p.changeQuantity(quantity);
        }
        return p;
    }

    @Test
    void stockStatusFollowsReorderLevel() {
        assertThat(product(0, 5).stockStatus()).isEqualTo(StockStatus.OUT);
        assertThat(product(5, 5).stockStatus()).isEqualTo(StockStatus.LOW);
        assertThat(product(6, 5).stockStatus()).isEqualTo(StockStatus.OK);
        // Reorder level 0: any positive quantity is OK.
        // حد إعادة الطلب صفر: أي كمية موجبة تعتبر OK.
        assertThat(product(1, 0).stockStatus()).isEqualTo(StockStatus.OK);
    }

    @Test
    void quantityCannotGoNegativeAndIsUnchangedOnFailure() {
        Product p = product(3, 0);
        assertThatThrownBy(() -> p.changeQuantity(-4))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(p.getQuantity()).isEqualTo(3);
        assertThat(p.changeQuantity(-3)).isZero();
    }

    @Test
    void vatIsRoundedOnceOnTheSubtotal() {
        // Three lines of 0.10: VAT per line would be 0.015 → 0.02 each = 0.06 in total (wrong).
        // VAT once on the subtotal: 0.30 × 15% = 0.045 → 0.05 (correct).
        // ثلاث أسطر بـ 0.10: لو حسبنا الضريبة لكل سطر = 0.015 ← 0.02 لكل سطر = 0.06 (غلط).
        // الضريبة مرة وحدة على المجموع: 0.30 × 15% = 0.045 ← 0.05 (صح).
        OrderTotals totals = OrderTotals.of(
                List.of(new BigDecimal("0.10"), new BigDecimal("0.10"), new BigDecimal("0.10")),
                new BigDecimal("0.15"));
        assertThat(totals.subtotal()).isEqualByComparingTo("0.30");
        assertThat(totals.vat()).isEqualByComparingTo("0.05");
        assertThat(totals.total()).isEqualByComparingTo("0.35");
    }

    @Test
    void weightedAverageCost() {
        Product p = product(10, 0);
        // 10 at 10.00 + 30 at 6.00 → (100 + 180) / 40 = 7.00
        PurchaseTestAccess.updateAverageCost(p, 10, 30, new BigDecimal("6.00"));
        assertThat(p.getCostPrice()).isEqualByComparingTo("7.00");
        // From zero stock the new cost is simply the received unit cost.
        // من مخزون صفر، التكلفة الجديدة هي سعر الوحدة المستلمة.
        Product empty = product(0, 0);
        PurchaseTestAccess.updateAverageCost(empty, 0, 5, new BigDecimal("3.33"));
        assertThat(empty.getCostPrice()).isEqualByComparingTo("3.33");
    }

    @Test
    void dailySalesUsesLocalDayAndFillsGaps() {
        ZoneId riyadh = ZoneId.of("Asia/Riyadh");
        LocalDate today = LocalDate.of(2026, 10, 26);
        // 22:30 UTC on Oct 25 is 01:30 on Oct 26 in Riyadh (UTC+3), so it must count for "today".
        // الساعة 22:30 UTC يوم 25 هي 1:30 فجرًا يوم 26 بتوقيت الرياض، فلازم تنحسب لـ "اليوم".
        SalesOrder lateNight = order("2026-10-25T22:30:00Z", "100.00");
        SalesOrder older = order("2026-10-20T09:00:00Z", "50.00");
        var days = DashboardTestAccess.dailySales(List.of(lateNight, older), today, riyadh);
        assertThat(days).hasSize(14);
        assertThat(days.get(13).date()).isEqualTo(today);
        assertThat(days.get(13).total()).isEqualByComparingTo("100.00");
        assertThat(days.get(7).date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(days.get(7).orders()).isEqualTo(1);
        assertThat(days.get(12).total()).isEqualByComparingTo("0");
    }

    private static SalesOrder order(String confirmedAt, String total) {
        SalesOrder order = new SalesOrder("SO-1", new Customer(), "test");
        ReflectionTestUtils.setField(order, "confirmedAt", Instant.parse(confirmedAt));
        ReflectionTestUtils.setField(order, "total", new BigDecimal(total));
        return order;
    }
}
