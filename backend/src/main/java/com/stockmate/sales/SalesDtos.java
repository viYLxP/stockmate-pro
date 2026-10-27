package com.stockmate.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stockmate.common.City;
import com.stockmate.order.LineRequest;
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentDtos.PaymentView;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record SalesOrderRequest(@NotNull Long customerId,
                                    @Size(max = 500) String notes,
                                    @NotNull @Size(min = 1, max = 100) List<@Valid LineRequest> lines) {
    }

    // The reason is optional for the office; it is shown to the shop customer. / السبب اختياري للمكتب؛ وينعرض لعميل المتجر.
    public record CancelRequest(@Size(max = 255) String reason) {
    }

    // cashCollected: the driver took the money of a cash-on-delivery order. / cashCollected: المندوب استلم فلوس طلب الدفع عند الاستلام.
    public record DeliverRequest(boolean cashCollected) {
    }

    // availableQuantity helps the UI warn before confirming if stock is not enough.
    // availableQuantity يساعد الواجهة تنبّه قبل التأكيد لو المخزون ما يكفي.
    public record LineResponse(Long productId, String productSku, String productName, String unit, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal, int availableQuantity) {

        static LineResponse from(SalesOrderLine l) {
            return new LineResponse(l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
                    l.getProduct().getUnit(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal(),
                    l.getProduct().getQuantity());
        }
    }

    // Where a shop order goes. / وين يروح طلب المتجر.
    public record ShipTo(String name, String phone, String email, City city, String district, String address) {

        public static ShipTo of(SalesOrder o) {
            return o.getChannel() != SalesChannel.ONLINE ? null : new ShipTo(o.getShipName(), o.getShipPhone(),
                    o.getShipEmail(), o.getShipCity(), o.getShipDistrict(), o.getShipAddress());
        }
    }

    public record SalesOrderSummary(Long id, String number, Long customerId, String customerName, SalesStatus status,
                                    SalesChannel channel, PaymentStatus paymentStatus, Payment.Method paymentChoice,
                                    Fulfilment fulfilment, BigDecimal total, Instant createdAt, Instant confirmedAt) {

        public static SalesOrderSummary from(SalesOrder o) {
            return new SalesOrderSummary(o.getId(), o.getNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                    o.getStatus(), o.getChannel(), o.getPaymentStatus(), o.getPaymentChoice(), o.getFulfilment(),
                    o.getTotal(), o.getCreatedAt(), o.getConfirmedAt());
        }
    }

    /**
     * The office view of an order. The shop fields (delivery, payment) are null for office orders.
     * عرض المكتب للطلب. حقول المتجر (التوصيل والدفع) null لطلبات المكتب.
     */
    public record SalesOrderResponse(Long id, String number, Long customerId, String customerName, SalesStatus status,
                                     String notes, BigDecimal subtotal, BigDecimal vat, BigDecimal total,
                                     String createdBy, Instant createdAt, Instant confirmedAt,
                                     List<LineResponse> lines,
                                     SalesChannel channel, PaymentStatus paymentStatus, Payment.Method paymentChoice,
                                     Fulfilment fulfilment, ShipTo shipTo, Instant payBy, Instant shippedAt,
                                     Instant deliveredAt, Instant cancelledAt, String cancelReason,
                                     List<PaymentView> payments) {

        public static SalesOrderResponse from(SalesOrder o) {
            return from(o, List.of());
        }

        public static SalesOrderResponse from(SalesOrder o, List<PaymentView> payments) {
            return new SalesOrderResponse(o.getId(), o.getNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                    o.getStatus(), o.getNotes(), o.getSubtotal(), o.getVat(), o.getTotal(), o.getCreatedBy(),
                    o.getCreatedAt(), o.getConfirmedAt(), o.getLines().stream().map(LineResponse::from).toList(),
                    o.getChannel(), o.getPaymentStatus(), o.getPaymentChoice(), o.getFulfilment(), ShipTo.of(o),
                    o.getPayBy(), o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(), o.getCancelReason(),
                    payments);
        }
    }
}
