package com.stockmate.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code stockmate.*} section of application.yml.
 * إعدادات التطبيق مربوطة بأنواع واضحة من قسم {@code stockmate.*} في application.yml.
 *
 * <p>{@code @Validated} makes the app refuse to start with an invalid configuration
 * (for example a JWT secret that is too short), instead of failing later at runtime.</p>
 * <p>{@code @Validated} يمنع التطبيق من التشغيل إذا كانت الإعدادات غير صالحة
 * (مثل مفتاح JWT قصير)، بدل ما يفشل لاحقًا أثناء الاستخدام.</p>
 */
@Validated
@ConfigurationProperties(prefix = "stockmate")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        // Saudi VAT rate (15%) used for every order total.
        // نسبة ضريبة القيمة المضافة في السعودية (15%) وتُستخدم في كل إجمالي طلب.
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal vatRate,
        // Business time zone used for "today" and "this month" in reports (Asia/Riyadh by default).
        // المنطقة الزمنية للنشاط وتُستخدم لتحديد "اليوم" و"هذا الشهر" في التقارير (الرياض افتراضيًا).
        @NotNull ZoneId timeZone,
        @Valid @NotNull Seed seed,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Payments payments,
        @Valid @NotNull Shop shop) {

    /**
     * JWT signing settings.
     * إعدادات توقيع رموز JWT.
     *
     * @param secret     HMAC-SHA256 key; at least 32 chars (256 bits) as required by the algorithm
     *                   / مفتاح HMAC-SHA256 ولازم 32 حرفًا على الأقل (256 بت) حسب متطلبات الخوارزمية
     * @param expiration how long a token stays valid / مدة صلاحية الرمز
     */
    public record Jwt(@NotNull @Size(min = 32) String secret, @NotNull Duration expiration) {
    }

    /**
     * Allowed browser origins for CORS (the Angular dev server in development).
     * مصادر المتصفح المسموح لها عبر CORS (خادم Angular أثناء التطوير).
     */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Demo data settings. Passwords come from environment variables, never hard-coded in production.
     * إعدادات البيانات التجريبية. كلمات المرور تأتي من متغيرات البيئة ولا تُكتب في الكود للإنتاج.
     */
    public record Seed(boolean enabled, String adminPassword, String staffPassword, String customerPassword) {
    }

    /**
     * Per-IP limits for the public endpoints within one time window.
     * حدود لكل عنوان IP على الروابط العامة خلال فترة زمنية وحدة.
     *
     * @param window   length of the counting window / طول فترة العدّ
     * @param orders   new online orders and sign-ups allowed per window / عدد الطلبات الجديدة والتسجيلات المسموحة في الفترة
     * @param lookups  order tracking and payment attempts allowed per window / عدد عمليات تتبع الطلب ومحاولات الدفع المسموحة
     */
    public record RateLimit(@NotNull Duration window, @Min(1) int orders, @Min(1) int lookups) {
    }

    /**
     * Online payments.
     * الدفع أونلاين.
     *
     * @param cardProvider who takes card and Apple Pay payments: SANDBOX (built-in test checkout, no account needed),
     *                     MOYASAR or STRIPE / مين ياخذ دفع البطاقات وApple Pay: SANDBOX (صفحة تجريبية مدمجة، ما تحتاج
     *                     حساب) أو MOYASAR أو STRIPE
     * @param publicUrl    the address customers open the site on; gateways send them back to it after paying
     *                     / عنوان الموقع اللي يفتحه العملاء؛ البوابات ترجّعهم له بعد الدفع
     * @param instalments  limits for Tabby and Tamara / حدود تابي وتمارا
     */
    public record Payments(@NotNull com.stockmate.payment.Payment.Provider cardProvider, @NotNull String publicUrl,
                           @Valid @NotNull Moyasar moyasar, @Valid @NotNull Stripe stripe,
                           @Valid @NotNull Tabby tabby, @Valid @NotNull Tamara tamara,
                           @Valid @NotNull Instalments instalments) {
    }

    // Keys are read only when set; without a key the gateway is simply not used. / المفاتيح تنقرأ بس لو موجودة.
    public record Moyasar(@NotNull String apiUrl, String secretKey) {
    }

    public record Stripe(@NotNull String apiUrl, String secretKey) {
    }

    // merchantCode is given by Tabby with the keys. / merchantCode تعطيك إياه تابي مع المفاتيح.
    public record Tabby(@NotNull String apiUrl, String secretKey, String merchantCode) {
    }

    public record Tamara(@NotNull String apiUrl, String apiToken) {
    }

    /**
     * Instalment plans. The real limits come from the contract with Tabby/Tamara; these defaults are only a start.
     * خطط التقسيط. الحدود الحقيقية تجي من العقد مع تابي وتمارا؛ وهذي القيم الافتراضية بداية بس.
     */
    public record Instalments(@NotNull java.math.BigDecimal minAmount, @NotNull java.math.BigDecimal maxAmount,
                              @Min(2) int count) {
    }

    /**
     * Online shop settings.
     * إعدادات المتجر الإلكتروني.
     *
     * @param unpaidHold how long an order paid online keeps its stock before it is cancelled unpaid
     *                   / كم يحتفظ الطلب المدفوع أونلاين بالمخزون قبل ما ينلغي لو ما اندفع
     */
    public record Shop(@NotNull Duration unpaidHold) {
    }
}
