package com.stockmate.partner;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A customer we sell to.
 * عميل نبيع له.
 */
@Entity
@Table(name = "customer")
public class Customer extends Partner {
}
