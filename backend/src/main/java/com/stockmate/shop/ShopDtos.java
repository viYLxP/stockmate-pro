package com.stockmate.shop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stockmate.common.City;
import com.stockmate.common.Money;
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentDtos.PaymentSummary;
import com.stockmate.product.Product;
import com.stockmate.sales.Fulfilment;
import com.stockmate.sales.SalesDtos.ShipTo;
import com.stockmate.sales.SalesOrder;
import com.stockmate.sales.SalesOrderLine;
import com.stockmate.sales.SalesStatus;

/**
 * Request/response shapes for the online shop. No cost prices and no exact stock: the shop is public.
 * أشكال الطلب والرد للمتجر الإلكتروني. بدون سعر التكلفة وبدون المخزون بالضبط: المتجر عام.
 */
public final class ShopDtos {

    // Above this, the shop only says "in stock"; at or below it, "only N left". / فوق هالعدد المتجر يقول "متوفر" بس؛ وتحته "باقي N".
    static final int LOW_STOCK_HINT = 5;

    private ShopDtos() {
    }

    /**
     * A product as shoppers see it. Prices include VAT, as Saudi shops must show them.
     * المنتج مثل ما يشوفه المتسوق. الأسعار شاملة الضريبة، مثل ما لازم تعرضها المتاجر في السعودية.
     *
     * @param left the exact count only when few are left, otherwise null / العدد بالضبط بس لو باقي قليل، وإلا null
     */
    public record ShopProduct(Long id, String sku, String name, String nameAr, String description,
                              String descriptionAr, Long categoryId, String categoryName, String categoryNameAr,
                              String unit, BigDecimal price, boolean inStock, Integer left) {

        static ShopProduct of(Product p, BigDecimal vatRate) {
            int qty = p.getQuantity();
            return new ShopProduct(p.getId(), p.getSku(), p.getName(), p.getNameAr(), p.getDescription(),
                    p.getDescriptionAr(), p.getCategory().getId(), p.getCategory().getName(),
                    p.getCategory().getNameAr(), p.getUnit(), withVat(p.getSalePrice(), vatRate), qty > 0,
                    qty > 0 && qty <= LOW_STOCK_HINT ? qty : null);
        }
    }

    static BigDecimal withVat(BigDecimal price, BigDecimal vatRate) {
        return Money.round(price.multiply(BigDecimal.ONE.add(vatRate)));
    }

    public record ShopCategory(Long id, String name, String nameAr, long productCount) {
    }

    public record CartItem(@NotNull Long productId, @Min(1) @Max(99) int quantity) {
    }

    public record QuoteRequest(@NotNull @Size(min = 1, max = 30) List<@Valid CartItem> items) {
    }

    /**
     * The cart priced by the server: what the customer will really pay, and which lines cannot be bought.
     * السلة مسعّرة من الخادم: اللي بيدفعه العميل فعلًا، وأي أسطر ما تنشرى.
     */
    public record Quote(List<QuoteLine> lines, BigDecimal subtotal, BigDecimal vat, BigDecimal total,
                        boolean canCheckout) {
    }

    /**
     * @param problem null when fine; UNAVAILABLE (not sold any more) or STOCK (fewer left than asked)
     *                / null لو تمام؛ UNAVAILABLE (ما عاد ينباع) أو STOCK (الباقي أقل من المطلوب)
     */
    public record QuoteLine(Long productId, String name, String nameAr, int quantity, BigDecimal unitPrice,
                            BigDecimal lineTotal, String problem, Integer available) {
    }

    /**
     * Checkout. A signed-in customer's name and phone come from the account; the form's values are ignored.
     * إتمام الطلب. اسم وجوال العميل المسجّل يجون من الحساب؛ وقيم النموذج تنتجاهل.
     */
    public record CheckoutRequest(
            @NotNull @Size(min = 1, max = 30) List<@Valid CartItem> items,
            @Size(max = 120) String fullName,
            @Size(max = 20) String phone,
            @Email @Size(max = 150) String email,
            @NotNull City city,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 300) String address,
            @NotNull Payment.Method paymentMethod,
            // Honeypot: hidden from people; bots fill every field. / مصيدة: مخفية عن الناس؛ والبوتات تعبّي كل الحقول.
            @Size(max = 200) String website) {
    }

    /**
     * @param checkoutUrl where to pay; null for cash on delivery / وين يدفع؛ null للدفع عند الاستلام
     */
    public record CheckoutResult(String number, BigDecimal total, Payment.Method paymentMethod, UUID paymentId,
                                 String checkoutUrl, Instant payBy) {
    }

    // The guest proves the order is theirs with the phone it was placed with. / الزائر يثبت إن الطلب له بالجوال اللي طلب فيه.
    public record TrackRequest(@NotBlank @Size(max = 20) String number, @NotBlank @Size(max = 20) String phone) {
    }

    public record GuestCancelRequest(@NotBlank @Size(max = 20) String phone) {
    }

    public record OrderLine(Long productId, String sku, String name, String nameAr, int quantity,
                            BigDecimal unitPrice, BigDecimal lineTotal) {

        static OrderLine of(SalesOrderLine l) {
            Product p = l.getProduct();
            return new OrderLine(p.getId(), p.getSku(), p.getName(), p.getNameAr(), l.getQuantity(),
                    l.getUnitPrice(), l.getLineTotal());
        }
    }

    /**
     * A shop order as its customer sees it (tracking page and account).
     * طلب المتجر مثل ما يشوفه صاحبه (صفحة التتبع والحساب).
     */
    public record OrderView(String number, SalesStatus status, Fulfilment fulfilment, Instant createdAt,
                            Instant payBy, Instant shippedAt, Instant deliveredAt, Instant cancelledAt,
                            String cancelReason, List<OrderLine> lines, BigDecimal subtotal, BigDecimal vat,
                            BigDecimal total, Payment.Method paymentChoice, PaymentSummary payment, ShipTo shipTo,
                            boolean cancellable) {

        static OrderView of(SalesOrder o, PaymentSummary payment, boolean cancellable) {
            return new OrderView(o.getNumber(), o.getStatus(), o.getFulfilment(), o.getCreatedAt(), o.getPayBy(),
                    o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(), o.getCancelReason(),
                    o.getLines().stream().map(OrderLine::of).toList(), o.getSubtotal(), o.getVat(), o.getTotal(),
                    o.getPaymentChoice(), payment, ShipTo.of(o), cancellable);
        }
    }

    // One row of "My orders". / سطر واحد في "طلباتي".
    public record OrderRow(String number, SalesStatus status, Fulfilment fulfilment, Instant createdAt,
                           int itemCount, BigDecimal total, Payment.Method paymentChoice,
                           com.stockmate.sales.PaymentStatus paymentStatus, boolean payable) {
    }
}
