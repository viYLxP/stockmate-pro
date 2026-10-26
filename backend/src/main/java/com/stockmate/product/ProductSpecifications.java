package com.stockmate.product;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composable filters for the product list (JPA Criteria API).
 * فلاتر قابلة للتركيب لقائمة المنتجات (JPA Criteria API).
 *
 * <p>Each method returns null when its filter is not used; ProductService drops the nulls before combining,
 * so we can combine any subset of filters without writing a query per combination.</p>
 * <p>كل دالة ترجّع null لو الفلتر غير مستخدم، وProductService يشيل القيم null قبل الدمج،
 * فنقدر نركّب أي مجموعة فلاتر بدون كتابة استعلام لكل احتمال.</p>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    // Free-text search on name or SKU.
    // بحث نصي في الاسم أو رمز الصنف.
    public static Specification<Product> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        // Escape LIKE wildcards so a user typing "%" or "_" searches for them literally.
        // نهرّب رموز LIKE حتى لو كتب المستخدم "%" أو "_" يبحث عنها كنص عادي.
        String pattern = "%" + q.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("sku")), pattern, '\\'));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> fromSupplier(Long supplierId) {
        return supplierId == null ? null : (root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId);
    }

    public static Specification<Product> active(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    // Same thresholds as Product.stockStatus(), expressed in SQL so filtering happens in the database.
    // نفس حدود Product.stockStatus() لكن بصيغة SQL حتى تتم الفلترة داخل قاعدة البيانات.
    public static Specification<Product> withStatus(StockStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> switch (status) {
            case OUT -> cb.equal(root.get("quantity"), 0);
            case LOW -> cb.and(cb.greaterThan(root.get("quantity"), 0),
                    cb.lessThanOrEqualTo(root.<Integer>get("quantity"), root.get("reorderLevel")));
            case OK -> cb.greaterThan(root.<Integer>get("quantity"), root.get("reorderLevel"));
        };
    }
}
