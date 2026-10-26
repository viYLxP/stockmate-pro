package com.stockmate.dashboard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.stockmate.dashboard.DashboardDtos.DailySales;
import com.stockmate.sales.SalesOrder;

/**
 * Exposes the package-private chart builder to unit tests.
 * يكشف دالة بناء الرسم الداخلية لاختبارات الوحدة.
 */
public final class DashboardTestAccess {

    private DashboardTestAccess() {
    }

    public static List<DailySales> dailySales(List<SalesOrder> orders, LocalDate today, ZoneId zone) {
        return DashboardService.dailySales(orders, today, zone);
    }
}
