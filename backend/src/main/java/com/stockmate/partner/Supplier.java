package com.stockmate.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A supplier we buy products from.
 * مورد نشتري منه المنتجات.
 */
@Entity
@Table(name = "supplier")
public class Supplier extends Partner {

    // Saudi VAT registration number (15 digits) shown on purchase documents.
    // الرقم الضريبي السعودي (15 رقمًا) ويظهر في مستندات الشراء.
    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }
}
