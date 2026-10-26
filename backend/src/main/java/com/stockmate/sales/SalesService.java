package com.stockmate.sales;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.common.ApiException;
import com.stockmate.common.CurrentUser;
import com.stockmate.common.ErrorCode;
import com.stockmate.common.PageResponse;
import com.stockmate.config.AppProperties;
import com.stockmate.order.LineRequest;
import com.stockmate.order.OrderLines;
import com.stockmate.partner.PartnerService;
import com.stockmate.product.Product;
import com.stockmate.product.ProductService;
import com.stockmate.sales.SalesDtos.SalesOrderRequest;
import com.stockmate.sales.SalesDtos.SalesOrderResponse;
import com.stockmate.sales.SalesDtos.SalesOrderSummary;
import com.stockmate.stock.MovementType;
import com.stockmate.stock.StockService;

/**
 * Sales order workflow: drafts, confirmation (stock out) and cancellation (stock back in).
 * سير عمل طلبات البيع: المسودات، التأكيد (خروج من المخزون)، والإلغاء (رجوع للمخزون).
 */
@Service
public class SalesService {

    private final SalesOrderRepository orders;
    private final PartnerService partners;
    private final ProductService products;
    private final StockService stock;
    private final AppProperties properties;

    public SalesService(SalesOrderRepository orders, PartnerService partners, ProductService products,
                        StockService stock, AppProperties properties) {
        this.orders = orders;
        this.partners = partners;
        this.products = products;
        this.stock = stock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSummary> search(SalesStatus status, Long customerId, String q, Pageable pageable) {
        return PageResponse.of(orders.search(status, customerId, q == null ? "" : q.trim(), pageable),
                SalesOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(Long id) {
        return SalesOrderResponse.from(load(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        OrderLines.validate(request.lines());
        String number = "SO-%06d".formatted(orders.nextNumber());
        SalesOrder order = new SalesOrder(number, partners.customer(request.customerId()), CurrentUser.username());
        apply(order, request);
        return SalesOrderResponse.from(orders.save(order));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        SalesOrder order = load(id);
        requireStatus(order, SalesStatus.DRAFT);
        OrderLines.validate(request.lines());
        order.setCustomer(partners.customer(request.customerId()));
        apply(order, request);
        return SalesOrderResponse.from(order);
    }

    /**
     * Confirms the order and takes every line out of stock, all-or-nothing.
     * يؤكد الطلب ويُخرج كل الأسطر من المخزون، يا كلها يا ولا شي.
     *
     * <p>If one product does not have enough stock, StockService throws INSUFFICIENT_STOCK, the transaction
     * rolls back, and no line is deducted. The error says which SKU failed and how many are available.</p>
     * <p>لو منتج واحد ما عنده كمية كافية، StockService يرمي INSUFFICIENT_STOCK وتتراجع المعاملة،
     * وما ينخصم أي سطر. والخطأ يوضح أي رمز صنف فشل وكم المتوفر منه.</p>
     */
    @Transactional
    public SalesOrderResponse confirm(Long id) {
        SalesOrder order = load(id);
        requireStatus(order, SalesStatus.DRAFT);
        // A product archived after the draft was created cannot be sold anymore.
        // المنتج اللي انأرشف بعد إنشاء المسودة ما يصير نبيعه.
        order.getLines().forEach(line -> OrderLines.requireActive(line.getProduct()));
        // Fixed lock order (by product id) prevents deadlocks between concurrent confirmations.
        // ترتيب قفل ثابت (حسب رقم المنتج) يمنع الـ deadlock بين تأكيدات متزامنة.
        sortedByProduct(order).forEach(line ->
                stock.record(line.getProduct().getId(), MovementType.SALE, line.getQuantity(), order.getNumber(), null));
        order.markConfirmed();
        return SalesOrderResponse.from(order);
    }

    /**
     * Cancels a draft, or cancels a confirmed order and returns its goods to stock.
     * يلغي مسودة، أو يلغي طلبًا مؤكدًا ويرجّع بضاعته للمخزون.
     */
    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrder order = load(id);
        if (order.getStatus() == SalesStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", order.getStatus().name()));
        }
        if (order.getStatus() == SalesStatus.CONFIRMED) {
            // Reverse each SALE movement with a SALE_RETURN, keeping both in the ledger for a full audit trail.
            // نعكس كل حركة بيع بحركة إرجاع، ونخلي الحركتين في الدفتر حتى يبقى السجل كامل.
            sortedByProduct(order).forEach(line -> stock.record(line.getProduct().getId(), MovementType.SALE_RETURN,
                    line.getQuantity(), order.getNumber(), null));
        }
        order.markCancelled();
        return SalesOrderResponse.from(order);
    }

    private void apply(SalesOrder order, SalesOrderRequest request) {
        order.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        List<SalesOrderLine> lines = request.lines().stream().map(this::toLine).toList();
        order.replaceLines(lines, properties.vatRate());
    }

    // Unit price defaults to the product's current sale price; a different price can be sent for discounts.
    // سعر الوحدة الافتراضي هو سعر البيع الحالي، ويقدر المستخدم يرسل سعر مختلف للخصومات.
    private SalesOrderLine toLine(LineRequest line) {
        Product product = products.product(line.productId());
        OrderLines.requireActive(product);
        BigDecimal price = line.unitPrice() != null ? line.unitPrice() : product.getSalePrice();
        return new SalesOrderLine(product, line.quantity(), price);
    }

    private static List<SalesOrderLine> sortedByProduct(SalesOrder order) {
        return order.getLines().stream().sorted(Comparator.comparing(l -> l.getProduct().getId())).toList();
    }

    private SalesOrder load(Long id) {
        return orders.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("salesOrder", id));
    }

    private static void requireStatus(SalesOrder order, SalesStatus expected) {
        if (order.getStatus() != expected) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", order.getStatus().name()));
        }
    }
}
