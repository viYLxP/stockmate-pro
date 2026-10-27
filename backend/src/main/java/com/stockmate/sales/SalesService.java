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
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentService;
import com.stockmate.product.Product;
import com.stockmate.product.ProductService;
import com.stockmate.common.Text;
import com.stockmate.sales.SalesDtos.SalesOrderRequest;
import com.stockmate.sales.SalesDtos.SalesOrderResponse;
import com.stockmate.sales.SalesDtos.SalesOrderSummary;
import com.stockmate.stock.MovementType;
import com.stockmate.stock.StockService;

/**
 * Sales order workflow: drafts, confirmation (stock out) and cancellation (stock back in), plus shipping and
 * delivering shop orders.
 * سير عمل طلبات البيع: المسودات، التأكيد (خروج من المخزون)، والإلغاء (رجوع للمخزون)، وكمان شحن وتوصيل طلبات
 * المتجر.
 */
@Service
public class SalesService {

    private final SalesOrderRepository orders;
    private final PartnerService partners;
    private final ProductService products;
    private final StockService stock;
    private final AppProperties properties;
    private final PaymentService payments;
    private final OrderLock locks;

    public SalesService(SalesOrderRepository orders, PartnerService partners, ProductService products,
                        StockService stock, AppProperties properties, PaymentService payments, OrderLock locks) {
        this.payments = payments;
        this.locks = locks;
        this.orders = orders;
        this.partners = partners;
        this.products = products;
        this.stock = stock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSummary> search(SalesStatus status, SalesChannel channel, Long customerId, String q,
                                                  Pageable pageable) {
        return PageResponse.of(orders.search(status, channel, customerId, q == null ? "" : q.trim(), pageable),
                SalesOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(Long id) {
        return view(load(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        OrderLines.validate(request.lines());
        String number = nextNumber();
        SalesOrder order = new SalesOrder(number, partners.customer(request.customerId()), CurrentUser.username());
        apply(order, request);
        return SalesOrderResponse.from(orders.save(order));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        SalesOrder order = load(id);
        requireStatus(order, SalesStatus.DRAFT);
        requireChannel(order, SalesChannel.OFFICE);
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
        confirmOrder(order);
        return SalesOrderResponse.from(order);
    }

    /**
     * Takes the lines out of stock and marks the order confirmed. Also used by the shop checkout.
     * يُخرج الأسطر من المخزون ويعلّم الطلب مؤكد. ويستخدمه الدفع في المتجر كمان.
     */
    public void confirmOrder(SalesOrder order) {
        // A product archived after the draft was created cannot be sold anymore.
        // المنتج اللي انأرشف بعد إنشاء المسودة ما يصير نبيعه.
        order.getLines().forEach(line -> OrderLines.requireActive(line.getProduct()));
        // Fixed lock order (by product id) prevents deadlocks between concurrent confirmations.
        // ترتيب قفل ثابت (حسب رقم المنتج) يمنع الـ deadlock بين تأكيدات متزامنة.
        sortedByProduct(order).forEach(line ->
                stock.record(line.getProduct().getId(), MovementType.SALE, line.getQuantity(), order.getNumber(), null));
        order.markConfirmed();
    }

    /**
     * Cancels a draft, or cancels a confirmed order and returns its goods to stock.
     * يلغي مسودة، أو يلغي طلبًا مؤكدًا ويرجّع بضاعته للمخزون.
     */
    @Transactional
    public SalesOrderResponse cancel(Long id, String reason) {
        SalesOrder order = loadLocked(id);
        cancelOrder(order, Text.blankToNull(reason));
        return view(order);
    }

    /**
     * Cancels an order: returns its goods to stock and, for a paid shop order, refunds the customer.
     * The caller locks the order first (see OrderLock).
     * يلغي الطلب: يرجّع بضاعته للمخزون، ولو كان طلب متجر مدفوع يرجّع الفلوس للعميل. واللي يناديها يقفل الطلب أول.
     */
    public void cancelOrder(SalesOrder order, String reason) {
        // A delivered order is finished; returns after delivery are a separate process.
        // الطلب اللي توصّل خلص؛ والإرجاع بعد التوصيل عملية ثانية.
        if (order.getStatus() == SalesStatus.CANCELLED || order.getFulfilment() == Fulfilment.DELIVERED) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", statusOf(order)));
        }
        if (order.getChannel() == SalesChannel.ONLINE) {
            payments.refundIfPaid(order);
        }
        if (order.getStatus() == SalesStatus.CONFIRMED) {
            // Reverse each SALE movement with a SALE_RETURN, keeping both in the ledger for a full audit trail.
            // نعكس كل حركة بيع بحركة إرجاع، ونخلي الحركتين في الدفتر حتى يبقى السجل كامل.
            sortedByProduct(order).forEach(line -> stock.record(line.getProduct().getId(), MovementType.SALE_RETURN,
                    line.getQuantity(), order.getNumber(), null));
        }
        order.markCancelled();
        order.setCancelReason(reason);
    }

    /**
     * Hands a shop order to delivery. An order paid online must be paid first; cash on delivery is paid at the door.
     * يسلّم طلب المتجر للتوصيل. الطلب المدفوع أونلاين لازم ينْدفع أول؛ والدفع عند الاستلام ينْدفع عند الباب.
     */
    @Transactional
    public SalesOrderResponse ship(Long id) {
        SalesOrder order = loadLocked(id);
        requireOpenOnline(order, Fulfilment.NEW);
        if (order.getPaymentStatus() != PaymentStatus.PAID && order.getPaymentChoice() != Payment.Method.CASH) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED, Map.of("paymentStatus",
                    order.getPaymentStatus().name()));
        }
        order.markShipped();
        return view(order);
    }

    /**
     * Marks a shipped order delivered. An unpaid order can only be delivered with its cash collected.
     * يعلّم الطلب المشحون إنه توصّل. والطلب اللي ما انْدفع ما يتوصّل إلا مع استلام الكاش.
     */
    @Transactional
    public SalesOrderResponse deliver(Long id, boolean cashCollected) {
        SalesOrder order = loadLocked(id);
        requireOpenOnline(order, Fulfilment.SHIPPED);
        if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
            if (!cashCollected) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("fields", Map.of("cashCollected", "CashRequired")));
            }
            payments.recordCash(order);
        }
        order.markDelivered();
        return view(order);
    }

    // Cash or a bank transfer the office received for a shop order. / كاش أو تحويل بنكي استلمه المكتب لطلب متجر.
    @Transactional
    public SalesOrderResponse recordCash(Long id) {
        SalesOrder order = loadLocked(id);
        payments.recordCash(order);
        return view(order);
    }

    private SalesOrderResponse view(SalesOrder order) {
        return SalesOrderResponse.from(order,
                order.getChannel() == SalesChannel.ONLINE ? payments.history(order) : List.of());
    }

    private static void requireOpenOnline(SalesOrder order, Fulfilment expected) {
        if (order.getChannel() != SalesChannel.ONLINE || order.getStatus() != SalesStatus.CONFIRMED
                || order.getFulfilment() != expected) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", statusOf(order)));
        }
    }

    // The status the UI should name in the error: delivery progress for shop orders. / الحالة اللي تذكرها الواجهة في الخطأ.
    private static String statusOf(SalesOrder order) {
        return order.getStatus() == SalesStatus.CONFIRMED && order.getFulfilment() != null
                ? order.getFulfilment().name() : order.getStatus().name();
    }

    // Shop orders are priced by the shop; the office cannot edit their lines. / طلبات المتجر سعّرها المتجر؛ والمكتب ما يعدّل أسطرها.
    private static void requireChannel(SalesOrder order, SalesChannel expected) {
        if (order.getChannel() != expected) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", "ONLINE"));
        }
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

    private SalesOrder loadLocked(Long id) {
        SalesOrder order = load(id);
        locks.lock(order);
        return order;
    }

    // For the shop and the payment timeout. / للمتجر ولانتهاء مهلة الدفع.
    public String nextNumber() {
        return "SO-%06d".formatted(orders.nextNumber());
    }

    private static void requireStatus(SalesOrder order, SalesStatus expected) {
        if (order.getStatus() != expected) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", order.getStatus().name()));
        }
    }
}
