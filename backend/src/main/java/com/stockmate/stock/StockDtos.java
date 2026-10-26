package com.stockmate.stock;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StockDtos {

    private StockDtos() {
    }

    public record MovementResponse(Long id, Long productId, String productSku, String productName, MovementType type,
                                   int quantity, int balanceAfter, String reference, String note, String createdBy,
                                   Instant createdAt) {

        public static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getSku(),
                    m.getProduct().getName(), m.getType(), m.getQuantity(), m.getBalanceAfter(), m.getReference(),
                    m.getNote(), m.getCreatedBy(), m.getCreatedAt());
        }
    }

    /**
     * Manual stock adjustment after a physical count.
     * تسوية مخزون يدوية بعد الجرد الفعلي.
     *
     * @param direction IN adds units, OUT removes them / IN يضيف و OUT ينقص
     * @param quantity  always positive; the direction decides the sign / دائمًا موجبة والاتجاه يحدد الإشارة
     * @param note      mandatory reason, so every correction is explained / سبب إلزامي حتى يكون كل تصحيح موضّح
     */
    public record AdjustmentRequest(@NotNull Long productId,
                                    @NotNull Direction direction,
                                    @Min(1) @Max(1_000_000) int quantity,
                                    @NotBlank @Size(max = 255) String note) {
    }

    public enum Direction {
        IN,
        OUT
    }
}
