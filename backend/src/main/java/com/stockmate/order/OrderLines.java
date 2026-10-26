package com.stockmate.order;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.product.Product;

/**
 * Validation rules shared by purchase and sales order lines.
 * قواعد تحقق مشتركة لأسطر طلبات الشراء والبيع.
 */
public final class OrderLines {

    private OrderLines() {
    }

    /**
     * Rejects empty orders and the same product appearing on two lines.
     * يرفض الطلب الفاضي، وتكرار نفس المنتج في سطرين.
     *
     * <p>Duplicates are rejected (not merged) so the user sees exactly what will be saved.</p>
     * <p>نرفض التكرار (ما ندمجه) حتى يشوف المستخدم بالضبط اللي بينحفظ.</p>
     */
    public static void validate(List<LineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw lineError("Required");
        }
        Set<Long> seen = new HashSet<>();
        for (LineRequest line : lines) {
            // Set.add returns false when the id is already present, i.e. a duplicate.
            // Set.add يرجّع false لو الرقم موجود مسبقًا، يعني تكرار.
            if (!seen.add(line.productId())) {
                throw lineError("DuplicateProduct");
            }
        }
    }

    /**
     * Archived products cannot be added to new orders.
     * المنتجات المؤرشفة ما تنضاف لطلبات جديدة.
     */
    public static void requireActive(Product product) {
        if (!product.isActive()) {
            throw lineError("InactiveProduct");
        }
    }

    // Same "fields" shape as Bean Validation errors, so the UI shows it under the lines table.
    // نفس شكل "fields" في أخطاء التحقق العادية، فتعرضه الواجهة تحت جدول الأسطر.
    private static ApiException lineError(String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("lines", reason)));
    }
}
