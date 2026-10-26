package com.stockmate.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * A user account that can log in to the system.
 * حساب مستخدم يقدر يسجّل دخول للنظام.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    // Only the BCrypt hash is stored, never the plain password.
    // نخزّن ناتج BCrypt فقط، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // Stored as text ("ADMIN") so adding a new role later does not shift existing values.
    // نخزّنها كنص ("ADMIN") حتى لا تتغير القيم القديمة لو أضفنا دورًا جديدًا لاحقًا.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Disabled users cannot log in, but their history (orders, movements) stays intact.
    // المستخدم الموقوف لا يقدر يدخل، لكن سجله (الطلبات والحركات) يبقى محفوظًا.
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // Required by JPA.
        // مطلوب من JPA.
    }

    public AppUser(String username, String fullName, String passwordHash, Role role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
