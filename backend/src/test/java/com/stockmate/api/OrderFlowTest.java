package com.stockmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockmate.support.IntegrationTest;

/**
 * End-to-end purchase and sales workflows, including VAT, stock effects and rollback on failure.
 * سير عمل المشتريات والمبيعات من البداية للنهاية، مع الضريبة وأثر المخزون والتراجع عند الفشل.
 */
class OrderFlowTest extends IntegrationTest {

    private String token;
    private long categoryId;
    private long supplierId;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        token = staffToken();
        categoryId = category(token, "General");
        supplierId = supplier(token, "Supplier A");
        customerId = customer(token, "Customer A");
    }

    @Test
    void receivingPurchaseAddsStockAndUpdatesWeightedAverageCost() throws Exception {
        long productId = product(token, "P-1", categoryId, "5.00", "9.00", 0, 10);
        long orderId = createId("/api/purchase-orders", """
                {"supplierId":%d,"lines":[{"productId":%d,"quantity":10,"unitPrice":7.00}]}
                """.formatted(supplierId, productId), token);
        mvc.perform(auth(get("/api/purchase-orders/" + orderId), token))
                .andExpect(jsonPath("$.number").value("PO-000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.subtotal").value(70.00))
                .andExpect(jsonPath("$.vat").value(10.50))
                .andExpect(jsonPath("$.total").value(80.50));
        // A draft does not touch stock.
        // المسودة ما تأثر على المخزون.
        assertThat(quantityOf(productId)).isEqualTo(10);

        mvc.perform(auth(post("/api/purchase-orders/" + orderId + "/receive"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());
        assertThat(quantityOf(productId)).isEqualTo(20);
        // (10 × 5.00 + 10 × 7.00) / 20 = 6.00
        assertThat(costOf(productId)).isEqualByComparingTo(new BigDecimal("6.00"));

        // Receiving twice would double the stock, so it must be blocked.
        // الاستلام مرتين يضاعف المخزون، فلازم يكون ممنوع.
        mvc.perform(auth(post("/api/purchase-orders/" + orderId + "/receive"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        assertThat(quantityOf(productId)).isEqualTo(20);
    }

    @Test
    void confirmingSaleDeductsStockAndCancellingReturnsIt() throws Exception {
        long productId = product(token, "S-1", categoryId, "3.00", "4.35", 0, 10);
        long orderId = createId("/api/sales-orders", """
                {"customerId":%d,"lines":[{"productId":%d,"quantity":3}]}
                """.formatted(customerId, productId), token);
        // Price defaults to the product sale price. 3 × 4.35 = 13.05; VAT 1.9575 → 1.96 (HALF_UP).
        // السعر الافتراضي هو سعر البيع. 3 × 4.35 = 13.05؛ والضريبة 1.9575 تتقرب إلى 1.96.
        mvc.perform(auth(get("/api/sales-orders/" + orderId), token))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(4.35))
                .andExpect(jsonPath("$.subtotal").value(13.05))
                .andExpect(jsonPath("$.vat").value(1.96))
                .andExpect(jsonPath("$.total").value(15.01));

        mvc.perform(auth(post("/api/sales-orders/" + orderId + "/confirm"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(quantityOf(productId)).isEqualTo(7);

        // A confirmed order can no longer be edited.
        // الطلب المؤكد ما يتعدل.
        mvc.perform(auth(put("/api/sales-orders/" + orderId), token).content("""
                        {"customerId":%d,"lines":[{"productId":%d,"quantity":1}]}
                        """.formatted(customerId, productId)))
                .andExpect(status().isConflict());

        mvc.perform(auth(post("/api/sales-orders/" + orderId + "/cancel"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(quantityOf(productId)).isEqualTo(10);
        // The ledger keeps OPENING, SALE and SALE_RETURN: nothing is erased.
        // الدفتر يحتفظ بالافتتاحي والبيع والإرجاع: ما ينمسح شي.
        mvc.perform(auth(get("/api/stock/movements?productId=" + productId), token))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("SALE_RETURN"));

        mvc.perform(auth(post("/api/sales-orders/" + orderId + "/cancel"), token))
                .andExpect(status().isConflict());
    }

    @Test
    void insufficientStockOnOneLineRollsBackTheWholeOrder() throws Exception {
        long plenty = product(token, "A-1", categoryId, "1", "2", 0, 100);
        long scarce = product(token, "B-1", categoryId, "1", "2", 0, 2);
        long orderId = createId("/api/sales-orders", """
                {"customerId":%d,"lines":[{"productId":%d,"quantity":5},{"productId":%d,"quantity":3}]}
                """.formatted(customerId, plenty, scarce), token);
        mvc.perform(auth(post("/api/sales-orders/" + orderId + "/confirm"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.sku").value("B-1"))
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.requested").value(3));
        // The first line was NOT deducted: the transaction rolled back completely.
        // السطر الأول ما انخصم: المعاملة تراجعت بالكامل.
        assertThat(quantityOf(plenty)).isEqualTo(100);
        assertThat(quantityOf(scarce)).isEqualTo(2);
        mvc.perform(auth(get("/api/sales-orders/" + orderId), token))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void duplicateOrInactiveProductsAreRejected() throws Exception {
        long productId = product(token, "D-1", categoryId, "1", "2", 0, 10);
        mvc.perform(auth(post("/api/sales-orders"), token).content("""
                        {"customerId":%d,"lines":[{"productId":%d,"quantity":1},{"productId":%d,"quantity":2}]}
                        """.formatted(customerId, productId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lines").value("DuplicateProduct"));
        mvc.perform(auth(post("/api/sales-orders"), token).content("""
                        {"customerId":%d,"lines":[]}
                        """.formatted(customerId)))
                .andExpect(status().isBadRequest());

        jdbc.update("update product set active = false where id = ?", productId);
        mvc.perform(auth(post("/api/sales-orders"), token).content("""
                        {"customerId":%d,"lines":[{"productId":%d,"quantity":1}]}
                        """.formatted(customerId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lines").value("InactiveProduct"));
    }

    @Test
    void dashboardReflectsRealData() throws Exception {
        long productId = product(token, "K-1", categoryId, "2.00", "5.00", 5, 10);
        product(token, "K-2", categoryId, "1.00", "1.00", 5, 0);
        long orderId = createId("/api/sales-orders", """
                {"customerId":%d,"lines":[{"productId":%d,"quantity":6}]}
                """.formatted(customerId, productId), token);
        mvc.perform(auth(post("/api/sales-orders/" + orderId + "/confirm"), token)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/dashboard"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProducts").value(2))
                // K-1 now has 4 left (≤ 5) → low; K-2 has 0 → out of stock.
                // K-1 باقي منه 4 (أقل من أو يساوي 5) ← منخفض؛ وK-2 صفر ← نافد.
                .andExpect(jsonPath("$.lowStockCount").value(1))
                .andExpect(jsonPath("$.outOfStockCount").value(1))
                .andExpect(jsonPath("$.inventoryCostValue").value(8.00))
                // 6 × 5.00 = 30.00 + 15% VAT = 34.50
                .andExpect(jsonPath("$.salesThisMonth").value(34.50))
                // 6 × (5.00 − 2.00) = 18.00
                .andExpect(jsonPath("$.grossProfitThisMonth").value(18.00))
                .andExpect(jsonPath("$.dailySales.length()").value(14))
                .andExpect(jsonPath("$.dailySales[13].total").value(34.50))
                .andExpect(jsonPath("$.topProducts[0].sku").value("K-1"))
                .andExpect(jsonPath("$.reorderList.length()").value(2));
    }
}
