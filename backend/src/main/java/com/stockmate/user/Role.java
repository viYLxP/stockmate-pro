package com.stockmate.user;

/**
 * System roles.
 * أدوار النظام.
 *
 * <p>ADMIN: full access, including deleting data and managing users.
 * STAFF: daily work (products, orders, stock) but cannot delete or manage users.</p>
 * <p>ADMIN: صلاحية كاملة تشمل الحذف وإدارة المستخدمين.
 * STAFF: العمل اليومي (منتجات، طلبات، مخزون) بدون حذف أو إدارة مستخدمين.</p>
 */
public enum Role {
    ADMIN,
    STAFF
}
