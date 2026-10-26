package com.stockmate.partner;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Shared contact fields for suppliers and customers.
 * حقول التواصل المشتركة بين الموردين والعملاء.
 *
 * <p>{@code @MappedSuperclass} reuses the column mappings without creating a shared table:
 * suppliers and customers still live in their own tables.</p>
 * <p>{@code @MappedSuperclass} يعيد استخدام تعريف الأعمدة بدون إنشاء جدول مشترك:
 * الموردين والعملاء يبقون في جداول منفصلة.</p>
 */
@MappedSuperclass
public abstract class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 80)
    private String city;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Copies the editable contact fields in one place so create and update stay consistent.
     * ينسخ حقول التواصل القابلة للتعديل في مكان واحد حتى يبقى الإنشاء والتعديل متطابقين.
     */
    public void applyContact(String name, String phone, String email, String city) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
