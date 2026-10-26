package com.stockmate.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stockmate.support.IntegrationTest;

/**
 * Product CRUD, filters, stock status and the rules that protect data history.
 * اختبارات المنتجات: الإضافة والتعديل والحذف، الفلاتر، حالة المخزون، وقواعد حماية السجل.
 */
class ProductApiTest extends IntegrationTest {

    private String token;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        token = staffToken();
        categoryId = category(token, "Groceries");
    }

    @Test
    void createWithOpeningQuantityRecordsAnOpeningMovement() throws Exception {
        long id = product(token, "gro-001", categoryId, "10.00", "15.00", 5, 12);
        mvc.perform(auth(get("/api/products/" + id), token))
                .andExpect(status().isOk())
                // SKU is normalised to upper case.
                // رمز الصنف يتحول لحروف كبيرة.
                .andExpect(jsonPath("$.sku").value("GRO-001"))
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.stockStatus").value("OK"))
                .andExpect(jsonPath("$.stockValue").value(120.00));
        mvc.perform(auth(get("/api/stock/movements?productId=" + id), token))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("OPENING"))
                .andExpect(jsonPath("$.content[0].quantity").value(12))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(12))
                .andExpect(jsonPath("$.content[0].createdBy").value("staff"));
    }

    @Test
    void duplicateSkuIsRejectedRegardlessOfCase() throws Exception {
        product(token, "ABC-1", categoryId, "1", "2", 0, 0);
        mvc.perform(auth(post("/api/products"), token).content("""
                        {"sku":"abc-1","name":"Other","categoryId":%d,"unit":"pc","costPrice":1,"salePrice":2}
                        """.formatted(categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"));
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        mvc.perform(auth(post("/api/products"), token).content("""
                        {"sku":"has space","name":"","categoryId":%d,"unit":"pc","costPrice":-1,"salePrice":2.555}
                        """.formatted(categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sku").value("Pattern"))
                .andExpect(jsonPath("$.fields.name").value("NotBlank"))
                .andExpect(jsonPath("$.fields.costPrice").value("DecimalMin"))
                .andExpect(jsonPath("$.fields.salePrice").value("Digits"));
    }

    @Test
    void unknownCategoryReturnsNotFound() throws Exception {
        mvc.perform(auth(post("/api/products"), token).content("""
                        {"sku":"X-1","name":"X","categoryId":9999,"unit":"pc","costPrice":1,"salePrice":2}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void filtersBySearchTextAndStockStatus() throws Exception {
        product(token, "OK-1", categoryId, "1", "2", 5, 50);
        product(token, "LOW-1", categoryId, "1", "2", 5, 5);
        product(token, "OUT-1", categoryId, "1", "2", 5, 0);
        mvc.perform(auth(get("/api/products?status=LOW"), token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("LOW-1"));
        mvc.perform(auth(get("/api/products?status=OUT"), token))
                .andExpect(jsonPath("$.content[0].sku").value("OUT-1"));
        mvc.perform(auth(get("/api/products?q=ok-"), token))
                .andExpect(jsonPath("$.totalElements").value(1));
        // "%" is escaped, so it does not match everything.
        // "%" مهرّب، فما يطابق كل شي.
        mvc.perform(auth(get("/api/products?q=%25"), token))
                .andExpect(jsonPath("$.totalElements").value(0));
        // An unknown sort field is a client error, not a server crash.
        // حقل ترتيب غير معروف خطأ من العميل، مو انهيار في الخادم.
        mvc.perform(auth(get("/api/products?sort=password"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWithStaleVersionIsRejected() throws Exception {
        long id = product(token, "V-1", categoryId, "1", "2", 0, 0);
        String update = """
                {"sku":"V-1","name":"Renamed","categoryId":%d,"unit":"pc","costPrice":1,"salePrice":3,
                 "reorderLevel":0,"active":true,"version":%d}
                """;
        mvc.perform(auth(put("/api/products/" + id), token).content(update.formatted(categoryId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.version").value(1));
        // A second user still holding version 0 must not overwrite the first user's change.
        // مستخدم ثاني لسا عنده النسخة 0 لازم ما يكتب فوق تعديل المستخدم الأول.
        mvc.perform(auth(put("/api/products/" + id), token).content(update.formatted(categoryId, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void productWithHistoryCannotBeDeletedButUnusedOneCan() throws Exception {
        String admin = adminToken();
        long used = product(token, "USED-1", categoryId, "1", "2", 0, 3);
        long unused = product(token, "NEW-1", categoryId, "1", "2", 0, 0);
        mvc.perform(auth(delete("/api/products/" + used), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IN_USE"));
        mvc.perform(auth(delete("/api/products/" + unused), admin)).andExpect(status().isNoContent());
        // The category still has one product, so it cannot be deleted either.
        // التصنيف لسا فيه منتج، فما ينحذف.
        mvc.perform(auth(delete("/api/categories/" + categoryId), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.productCount").value(1));
    }

    @Test
    void manualAdjustmentsChangeStockAndCannotGoNegative() throws Exception {
        long id = product(token, "ADJ-1", categoryId, "1", "2", 0, 4);
        mvc.perform(auth(post("/api/stock/adjustments"), token)
                        .content("{\"productId\":%d,\"direction\":\"OUT\",\"quantity\":3,\"note\":\"Damaged\"}".formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(-3))
                .andExpect(jsonPath("$.balanceAfter").value(1));
        mvc.perform(auth(post("/api/stock/adjustments"), token)
                        .content("{\"productId\":%d,\"direction\":\"OUT\",\"quantity\":2,\"note\":\"Lost\"}".formatted(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.available").value(1));
        // A note is mandatory so every manual change is explained.
        // الملاحظة إلزامية حتى يكون كل تغيير يدوي موضّح.
        mvc.perform(auth(post("/api/stock/adjustments"), token)
                        .content("{\"productId\":%d,\"direction\":\"IN\",\"quantity\":2,\"note\":\" \"}".formatted(id)))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(quantityOf(id)).isEqualTo(1);
    }
}
