package com.stockmate.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PartnerDtos {

    private PartnerDtos() {
    }

    // Phone: optional; digits, spaces, "+" and "-" only (e.g. +966 50 123 4567).
    // الجوال اختياري، ويقبل أرقام ومسافات و"+" و"-" فقط (مثل ‎+966 50 123 4567).
    static final String PHONE_REGEX = "^$|^[+0-9][0-9 \\-]{6,19}$";

    public record SupplierRequest(
            @NotBlank @Size(max = 150) String name,
            @Pattern(regexp = PHONE_REGEX) String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 80) String city,
            // Saudi VAT numbers are exactly 15 digits; empty is allowed.
            // الرقم الضريبي السعودي 15 رقمًا بالضبط، ومسموح يكون فاضي.
            @Pattern(regexp = "^$|^[0-9]{15}$") String vatNumber) {
    }

    public record SupplierResponse(Long id, String name, String phone, String email, String city, String vatNumber) {

        public static SupplierResponse from(Supplier s) {
            return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getCity(), s.getVatNumber());
        }
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 150) String name,
            @Pattern(regexp = PHONE_REGEX) String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 80) String city) {
    }

    public record CustomerResponse(Long id, String name, String phone, String email, String city) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getCity());
        }
    }

    /**
     * Trims a value and converts blank strings to null, so the database never stores "" for optional fields.
     * يقص المسافات ويحوّل النص الفاضي إلى null، حتى لا تخزّن قاعدة البيانات "" في الحقول الاختيارية.
     */
    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
