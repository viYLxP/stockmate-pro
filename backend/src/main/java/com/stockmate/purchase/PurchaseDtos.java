package com.stockmate.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stockmate.order.LineRequest;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    // @Valid on the list validates every line, not just the list itself.
    // @Valid على القائمة يتحقق من كل سطر، مو بس من القائمة نفسها.
    public record PurchaseOrderRequest(@NotNull Long supplierId,
                                       @Size(max = 500) String notes,
                                       @NotNull @Size(min = 1, max = 100) List<@Valid LineRequest> lines) {
    }

    public record LineResponse(Long productId, String productSku, String productName, String unit, int quantity,
                               BigDecimal unitCost, BigDecimal lineTotal) {

        static LineResponse from(PurchaseOrderLine l) {
            return new LineResponse(l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
                    l.getProduct().getUnit(), l.getQuantity(), l.getUnitCost(), l.getLineTotal());
        }
    }

    // Summary row for the list page (no lines, keeps the list query light).
    // صف مختصر لصفحة القائمة (بدون أسطر، حتى يبقى استعلام القائمة خفيف).
    public record PurchaseOrderSummary(Long id, String number, Long supplierId, String supplierName,
                                       PurchaseStatus status, BigDecimal total, Instant createdAt, Instant receivedAt) {

        public static PurchaseOrderSummary from(PurchaseOrder o) {
            return new PurchaseOrderSummary(o.getId(), o.getNumber(), o.getSupplier().getId(), o.getSupplier().getName(),
                    o.getStatus(), o.getTotal(), o.getCreatedAt(), o.getReceivedAt());
        }
    }

    public record PurchaseOrderResponse(Long id, String number, Long supplierId, String supplierName,
                                        PurchaseStatus status, String notes, BigDecimal subtotal, BigDecimal vat,
                                        BigDecimal total, String createdBy, Instant createdAt, Instant receivedAt,
                                        List<LineResponse> lines) {

        public static PurchaseOrderResponse from(PurchaseOrder o) {
            return new PurchaseOrderResponse(o.getId(), o.getNumber(), o.getSupplier().getId(), o.getSupplier().getName(),
                    o.getStatus(), o.getNotes(), o.getSubtotal(), o.getVat(), o.getTotal(), o.getCreatedBy(),
                    o.getCreatedAt(), o.getReceivedAt(), o.getLines().stream().map(LineResponse::from).toList());
        }
    }
}
