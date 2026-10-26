package com.stockmate.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stockmate.order.LineRequest;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record SalesOrderRequest(@NotNull Long customerId,
                                    @Size(max = 500) String notes,
                                    @NotNull @Size(min = 1, max = 100) List<@Valid LineRequest> lines) {
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

    public record SalesOrderSummary(Long id, String number, Long customerId, String customerName, SalesStatus status,
                                    BigDecimal total, Instant createdAt, Instant confirmedAt) {

        public static SalesOrderSummary from(SalesOrder o) {
            return new SalesOrderSummary(o.getId(), o.getNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                    o.getStatus(), o.getTotal(), o.getCreatedAt(), o.getConfirmedAt());
        }
    }

    public record SalesOrderResponse(Long id, String number, Long customerId, String customerName, SalesStatus status,
                                     String notes, BigDecimal subtotal, BigDecimal vat, BigDecimal total,
                                     String createdBy, Instant createdAt, Instant confirmedAt,
                                     List<LineResponse> lines) {

        public static SalesOrderResponse from(SalesOrder o) {
            return new SalesOrderResponse(o.getId(), o.getNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                    o.getStatus(), o.getNotes(), o.getSubtotal(), o.getVat(), o.getTotal(), o.getCreatedBy(),
                    o.getCreatedAt(), o.getConfirmedAt(), o.getLines().stream().map(LineResponse::from).toList());
        }
    }
}
