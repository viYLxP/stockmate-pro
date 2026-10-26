package com.stockmate.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic helpers.
 * أدوات حساب المبالغ المالية.
 *
 * <p>We use BigDecimal, not double, because double has rounding errors (0.1 + 0.2 != 0.3),
 * which is unacceptable for invoices and VAT.</p>
 * <p>نستخدم BigDecimal بدل double لأن double فيه أخطاء تقريب (0.1 + 0.2 ≠ 0.3)،
 * وهذا غير مقبول في الفواتير والضريبة.</p>
 */
public final class Money {

    private Money() {
        // Utility class: no instances.
        // كلاس أدوات فقط، لا نسمح بإنشاء نسخة منه.
    }

    /**
     * Rounds to 2 decimals (halalas) using standard accounting rounding HALF_UP.
     * يقرّب المبلغ إلى منزلتين (هللات) بالتقريب المحاسبي المعتاد HALF_UP.
     */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Unit price × quantity, rounded to 2 decimals.
     * سعر الوحدة × الكمية، مقرّبًا إلى منزلتين.
     */
    public static BigDecimal times(BigDecimal price, int quantity) {
        return round(price.multiply(BigDecimal.valueOf(quantity)));
    }
}
