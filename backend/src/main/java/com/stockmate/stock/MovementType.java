package com.stockmate.stock;

/**
 * Why the stock changed. Each type has a fixed direction, checked in StockService.
 * سبب تغيّر المخزون. لكل نوع اتجاه ثابت (دخول أو خروج) يتحقق منه StockService.
 */
public enum MovementType {
    // Initial quantity entered when the product was created.
    // الكمية الافتتاحية عند إنشاء المنتج.
    OPENING(true),
    // Goods received from a purchase order.
    // بضاعة مستلمة من طلب شراء.
    PURCHASE(true),
    // Goods shipped for a confirmed sales order.
    // بضاعة خرجت لطلب بيع مؤكد.
    SALE(false),
    // Stock returned because a confirmed sale was cancelled.
    // بضاعة رجعت بسبب إلغاء بيع مؤكد.
    SALE_RETURN(true),
    // Manual correction after a stock count: found extra units.
    // تصحيح يدوي بعد الجرد: وجدنا كمية زائدة.
    ADJUSTMENT_IN(true),
    // Manual correction: damaged, expired or missing units.
    // تصحيح يدوي: تالف أو منتهي أو مفقود.
    ADJUSTMENT_OUT(false);

    private final boolean inbound;

    MovementType(boolean inbound) {
        this.inbound = inbound;
    }

    /**
     * True when this movement adds stock.
     * صحيح إذا كانت الحركة تضيف للمخزون.
     */
    public boolean isInbound() {
        return inbound;
    }
}
