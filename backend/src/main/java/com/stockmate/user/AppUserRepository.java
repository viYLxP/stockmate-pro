package com.stockmate.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for users. Spring Data generates the SQL from the method names.
 * الوصول لجدول المستخدمين. Spring Data يولّد استعلامات SQL من أسماء الدوال.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Case-insensitive so "Admin" and "admin" are treated as the same account.
    // بدون حساسية لحالة الأحرف حتى يُعامل "Admin" و "admin" كحساب واحد.
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndActiveTrue(Role role);
}
