package com.stockmate.user;

/**
 * System roles.
 * أدوار النظام.
 *
 * <p>ADMIN: full access, including deleting data and managing users.
 * STAFF: daily work (products, orders, stock) but cannot delete or manage users.
 * CUSTOMER: a shopper with an account.</p>
 * <p>ADMIN: صلاحية كاملة تشمل الحذف وإدارة المستخدمين.
 * STAFF: العمل اليومي (منتجات، طلبات، مخزون) بدون حذف أو إدارة مستخدمين.
 * CUSTOMER: متسوّق عنده حساب.</p>
 */
public enum Role {
    ADMIN,
    STAFF,
    // Signed up on the shop; can only use the shop and their own account, never the back office.
    // سجّل من المتجر؛ يستخدم المتجر وحسابه بس، وما يدخل لوحة الإدارة أبدًا.
    CUSTOMER;

    public boolean isStaff() {
        return this != CUSTOMER;
    }
}
