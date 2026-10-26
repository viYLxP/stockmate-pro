package com.stockmate.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API.
 * رموز أخطاء ثابتة يرجّعها الـ API ويفهمها البرنامج.
 *
 * <p>The frontend translates these codes into Arabic or English, so the API never returns
 * user-facing text. This keeps server logic independent of the display language.</p>
 * <p>الواجهة الأمامية تترجم هذه الرموز إلى العربية أو الإنجليزية، فلا يرجّع الـ API أي نص
 * موجّه للمستخدم. هذا يفصل منطق الخادم عن لغة العرض.</p>
 */
public enum ErrorCode {
    // Request data is invalid (missing field, negative number, malformed JSON...).
    // بيانات الطلب غير صحيحة (حقل ناقص، رقم سالب، JSON تالف...).
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // Wrong username/password, or the account is disabled.
    // اسم المستخدم أو كلمة المرور خطأ، أو الحساب موقوف.
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    // Logged in, but the role does not allow this action.
    // المستخدم مسجّل دخول لكن صلاحيته لا تسمح بالعملية.
    FORBIDDEN(HttpStatus.FORBIDDEN),
    // The requested record does not exist.
    // السجل المطلوب غير موجود.
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // The product SKU is already taken.
    // رمز المنتج (SKU) مستخدم مسبقًا.
    SKU_EXISTS(HttpStatus.CONFLICT),
    // The username is already taken.
    // اسم المستخدم محجوز.
    USERNAME_EXISTS(HttpStatus.CONFLICT),
    // Duplicate name (for example a category name).
    // الاسم مكرر (مثل اسم تصنيف).
    NAME_EXISTS(HttpStatus.CONFLICT),
    // Cannot delete because other records reference it (e.g. a category that still has products).
    // لا يمكن الحذف لأن سجلات أخرى مرتبطة به (مثل تصنيف فيه منتجات).
    IN_USE(HttpStatus.CONFLICT),
    // Requested quantity is larger than the stock on hand.
    // الكمية المطلوبة أكبر من المتوفر في المخزون.
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    // Action not allowed in the order's current status (e.g. receiving a cancelled order).
    // العملية غير مسموحة في حالة الطلب الحالية (مثل استلام طلب ملغي).
    INVALID_STATUS(HttpStatus.CONFLICT),
    // Someone else changed the same record meanwhile (optimistic locking).
    // شخص آخر عدّل نفس السجل في نفس الوقت (Optimistic Locking).
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    // An admin cannot disable or demote their own account, so the system is never locked out.
    // منع المدير من إيقاف حسابه أو تغيير صلاحيته بنفسه حتى لا يُقفل النظام.
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT),
    // Unexpected server error.
    // خطأ غير متوقع في الخادم.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    // The HTTP status for each code, kept in one place.
    // حالة HTTP المناسبة لكل رمز، محفوظة في مكان واحد.
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
