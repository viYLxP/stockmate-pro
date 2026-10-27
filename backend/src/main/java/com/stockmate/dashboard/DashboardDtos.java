package com.stockmate.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stockmate.product.ProductDtos.ProductResponse;
import com.stockmate.stock.StockDtos.MovementResponse;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * Everything the dashboard page needs in a single request.
     * كل اللي تحتاجه صفحة لوحة التحكم في طلب واحد.
     */
    public record DashboardResponse(
            long activeProducts,
            long lowStockCount,
            long outOfStockCount,
            BigDecimal inventoryCostValue,
            BigDecimal inventoryRetailValue,
            BigDecimal salesThisMonth,
            BigDecimal grossProfitThisMonth,
            BigDecimal purchasesThisMonth,
            long draftSalesOrders,
            long draftPurchaseOrders,
            List<DailySales> dailySales,
            List<TopProductRow> topProducts,
            List<ProductResponse> reorderList,
            List<MovementResponse> recentMovements,
            Shop shop) {
    }

    /**
     * The online shop at a glance: orders waiting for the warehouse, and money received online this month.
     * المتجر الإلكتروني بنظرة: الطلبات اللي تنتظر المستودع، والفلوس اللي وصلت أونلاين هالشهر.
     */
    public record Shop(long toShip, long outForDelivery, BigDecimal collectedThisMonth) {
    }

    public record DailySales(LocalDate date, BigDecimal total, int orders) {
    }

    public record TopProductRow(Long productId, String sku, String name, long quantity, BigDecimal revenue) {
    }
}
