package com.stockmate.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

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
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderRequest;
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderResponse;
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderSummary;
import com.stockmate.stock.MovementType;
import com.stockmate.stock.StockMovement;
import com.stockmate.stock.StockService;

/**
 * Purchase order workflow: create/edit drafts, receive goods into stock, or cancel.
 * سير عمل طلبات الشراء: إنشاء وتعديل المسودات، استلام البضاعة للمخزون، أو الإلغاء.
 */
@Service
public class PurchaseService {

    private final PurchaseOrderRepository orders;
    private final PartnerService partners;
    private final ProductService products;
    private final StockService stock;
    private final AppProperties properties;

    public PurchaseService(PurchaseOrderRepository orders, PartnerService partners, ProductService products,
                           StockService stock, AppProperties properties) {
        this.orders = orders;
        this.partners = partners;
        this.products = products;
        this.stock = stock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderSummary> search(PurchaseStatus status, Long supplierId, String q, Pageable pageable) {
        return PageResponse.of(orders.search(status, supplierId, q == null ? "" : q.trim(), pageable),
                PurchaseOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        return PurchaseOrderResponse.from(load(id));
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {
        OrderLines.validate(request.lines());
        // Format the sequence value as a 6-digit number, e.g. 42 → "PO-000042".
        // نحوّل قيمة الـ sequence لرقم من 6 خانات، مثل 42 ← "PO-000042".
        String number = "PO-%06d".formatted(orders.nextNumber());
        PurchaseOrder order = new PurchaseOrder(number, partners.supplier(request.supplierId()), CurrentUser.username());
        apply(order, request);
        return PurchaseOrderResponse.from(orders.save(order));
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {
        PurchaseOrder order = load(id);
        // Only drafts are editable; received/cancelled orders are a historical record.
        // فقط المسودات قابلة للتعديل؛ الطلبات المستلمة أو الملغية سجل تاريخي.
        requireDraft(order);
        OrderLines.validate(request.lines());
        order.setSupplier(partners.supplier(request.supplierId()));
        apply(order, request);
        return PurchaseOrderResponse.from(order);
    }

    /**
     * Receives every line into stock and updates each product's cost with a weighted average.
     * يستلم كل الأسطر للمخزون ويحدّث تكلفة كل منتج بالمتوسط المرجّح.
     *
     * <p>Everything runs in one transaction: if any line fails, nothing is received.</p>
     * <p>كل شي في معاملة وحدة: لو فشل أي سطر، ما ينستلم شي.</p>
     */
    @Transactional
    public PurchaseOrderResponse receive(Long id) {
        PurchaseOrder order = load(id);
        requireDraft(order);
        // Lock products in a fixed order (by id) so two transactions can never wait on each other in a cycle (deadlock).
        // نقفل المنتجات بترتيب ثابت (حسب الرقم) حتى ما تنتظر معاملتين بعض بشكل دائري (deadlock).
        List<PurchaseOrderLine> sorted = order.getLines().stream()
                .sorted(Comparator.comparing(line -> line.getProduct().getId()))
                .toList();
        for (PurchaseOrderLine line : sorted) {
            StockMovement movement = stock.record(line.getProduct().getId(), MovementType.PURCHASE,
                    line.getQuantity(), order.getNumber(), null);
            updateAverageCost(movement.getProduct(), movement.getBalanceAfter() - line.getQuantity(),
                    line.getQuantity(), line.getUnitCost());
        }
        order.markReceived();
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrder order = load(id);
        requireDraft(order);
        order.markCancelled();
        return PurchaseOrderResponse.from(order);
    }

    /**
     * Weighted average cost: (oldQty × oldCost + receivedQty × unitCost) ÷ (oldQty + receivedQty).
     * متوسط التكلفة المرجّح: (الكمية القديمة × التكلفة القديمة + الكمية المستلمة × سعر الوحدة) ÷ (المجموع).
     *
     * <p>Example: 10 units at 5.00 + 10 units received at 7.00 → new cost 6.00. This keeps the inventory
     * value accurate when supplier prices change.</p>
     * <p>مثال: 10 حبات بـ 5.00 واستلمنا 10 بـ 7.00 ← التكلفة الجديدة 6.00. هذا يخلي قيمة المخزون دقيقة
     * لما تتغير أسعار الموردين.</p>
     */
    static void updateAverageCost(Product product, int oldQty, int receivedQty, BigDecimal unitCost) {
        int newQty = oldQty + receivedQty;
        // Defensive: newQty is always > 0 here because receivedQty >= 1, but never divide by zero.
        // احتياط: newQty دائمًا أكبر من صفر هنا لأن receivedQty ≥ 1، لكن ما نقسم على صفر أبدًا.
        if (newQty <= 0) {
            return;
        }
        BigDecimal oldValue = product.getCostPrice().multiply(BigDecimal.valueOf(oldQty));
        BigDecimal newValue = unitCost.multiply(BigDecimal.valueOf(receivedQty));
        product.setCostPrice(oldValue.add(newValue).divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP));
    }

    /**
     * Builds the order lines from the request, defaulting the unit cost to the product's current cost.
     * يبني أسطر الطلب من الطلب المرسل، ويستخدم تكلفة المنتج الحالية لو ما انرسل سعر.
     */
    private void apply(PurchaseOrder order, PurchaseOrderRequest request) {
        order.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        List<PurchaseOrderLine> lines = request.lines().stream().map(this::toLine).toList();
        order.replaceLines(lines, properties.vatRate());
    }

    private PurchaseOrderLine toLine(LineRequest line) {
        Product product = products.product(line.productId());
        OrderLines.requireActive(product);
        BigDecimal unitCost = line.unitPrice() != null ? line.unitPrice() : product.getCostPrice();
        return new PurchaseOrderLine(product, line.quantity(), unitCost);
    }

    private PurchaseOrder load(Long id) {
        return orders.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("purchaseOrder", id));
    }

    private static void requireDraft(PurchaseOrder order) {
        if (order.getStatus() != PurchaseStatus.DRAFT) {
            // Tell the UI the current status so it can explain why the action is blocked.
            // نخبر الواجهة بالحالة الحالية حتى توضح سبب منع العملية.
            throw new ApiException(ErrorCode.INVALID_STATUS, java.util.Map.of("status", order.getStatus().name()));
        }
    }
}
