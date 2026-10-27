package com.stockmate.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.config.AppProperties;
import com.stockmate.dashboard.DashboardDtos.DailySales;
import com.stockmate.dashboard.DashboardDtos.DashboardResponse;
import com.stockmate.dashboard.DashboardDtos.TopProductRow;
import com.stockmate.product.ProductDtos.ProductResponse;
import com.stockmate.product.ProductRepository;
import com.stockmate.purchase.PurchaseOrderRepository;
import com.stockmate.purchase.PurchaseStatus;
import com.stockmate.payment.PaymentRepository;
import com.stockmate.sales.Fulfilment;
import com.stockmate.sales.SalesOrder;
import com.stockmate.sales.SalesOrderRepository;
import com.stockmate.sales.SalesStatus;
import com.stockmate.stock.StockDtos.MovementResponse;
import com.stockmate.stock.StockMovementRepository;

/**
 * Builds the dashboard numbers from live data (no cached or hard-coded figures).
 * يبني أرقام لوحة التحكم من البيانات الحية (بدون أرقام مخزّنة مسبقًا أو ثابتة).
 */
@Service
public class DashboardService {

    // Length of the daily sales chart.
    // عدد أيام رسم المبيعات اليومي.
    static final int CHART_DAYS = 14;

    private final ProductRepository products;
    private final SalesOrderRepository sales;
    private final PurchaseOrderRepository purchases;
    private final StockMovementRepository movements;
    private final AppProperties properties;
    private final PaymentRepository payments;

    public DashboardService(ProductRepository products, SalesOrderRepository sales, PurchaseOrderRepository purchases,
                            StockMovementRepository movements, AppProperties properties, PaymentRepository payments) {
        this.payments = payments;
        this.products = products;
        this.sales = sales;
        this.purchases = purchases;
        this.movements = movements;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        ZoneId zone = properties.timeZone();
        LocalDate today = LocalDate.now(zone);
        // Month boundaries in the business time zone, converted to UTC instants for the database.
        // حدود الشهر بتوقيت النشاط، محوّلة إلى لحظات UTC لقاعدة البيانات.
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant chartStart = today.minusDays(CHART_DAYS - 1L).atStartOfDay(zone).toInstant();

        return new DashboardResponse(
                products.countByActiveTrue(),
                products.countLowStock(),
                products.countOutOfStock(),
                products.inventoryCostValue(),
                products.inventoryRetailValue(),
                sales.confirmedTotal(monthStart, tomorrow),
                sales.grossProfit(monthStart, tomorrow),
                purchases.receivedTotal(monthStart, tomorrow),
                sales.countByStatus(SalesStatus.DRAFT),
                purchases.countByStatus(PurchaseStatus.DRAFT),
                dailySales(sales.findConfirmedSince(chartStart), today, zone),
                sales.topProducts(monthStart, PageRequest.of(0, 5)).stream()
                        .map(row -> new TopProductRow(row.getProductId(), row.getSku(), row.getName(),
                                row.getQuantity(), row.getRevenue()))
                        .toList(),
                products.findReorderList(PageRequest.of(0, 8)).stream().map(ProductResponse::from).toList(),
                movements.findTop8ByOrderByCreatedAtDescIdDesc().stream().map(MovementResponse::from).toList(),
                new DashboardDtos.Shop(sales.countOpenOnline(Fulfilment.NEW), sales.countOpenOnline(Fulfilment.SHIPPED),
                        payments.collected(monthStart, tomorrow)));
    }

    /**
     * Groups confirmed orders by local calendar day, filling days with no sales with zero
     * so the chart always has exactly {@link #CHART_DAYS} points.
     * يجمّع الطلبات المؤكدة حسب اليوم المحلي، ويعبّي الأيام اللي ما فيها مبيعات بصفر،
     * حتى يكون في الرسم دائمًا {@link #CHART_DAYS} نقطة بالضبط.
     */
    static List<DailySales> dailySales(List<SalesOrder> orders, LocalDate today, ZoneId zone) {
        // LinkedHashMap keeps days in chronological order (oldest → today).
        // LinkedHashMap يحافظ على ترتيب الأيام زمنيًا (الأقدم ← اليوم).
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            totals.put(today.minusDays(i), BigDecimal.ZERO);
            counts.put(today.minusDays(i), 0);
        }
        for (SalesOrder order : orders) {
            // Convert the UTC instant to the local day; an order at 01:00 Riyadh time belongs to that local day.
            // نحوّل لحظة UTC إلى اليوم المحلي؛ طلب الساعة 1 فجرًا بتوقيت الرياض يتبع ذاك اليوم المحلي.
            LocalDate day = ZonedDateTime.ofInstant(order.getConfirmedAt(), zone).toLocalDate();
            // Ignore anything outside the window (defensive: the query already filters by start date).
            // نتجاهل أي شي خارج النافذة (احتياط: الاستعلام أصلًا يفلتر بتاريخ البداية).
            if (totals.containsKey(day)) {
                totals.merge(day, order.getTotal(), BigDecimal::add);
                counts.merge(day, 1, Integer::sum);
            }
        }
        List<DailySales> result = new ArrayList<>();
        totals.forEach((day, total) -> result.add(new DailySales(day, total, counts.get(day))));
        return result;
    }
}
