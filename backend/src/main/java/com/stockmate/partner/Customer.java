package com.stockmate.partner;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.stockmate.user.AppUser;

/**
 * A customer we sell to: added by the office, created by a guest checkout, or linked to a shop account.
 * عميل نبيع له: يضيفه المكتب، أو ينشأ من طلب زائر في المتجر، أو مربوط بحساب في المتجر.
 */
@Entity
@Table(name = "customer")
public class Customer extends Partner {

    // Set for customers who signed up on the shop. / موجود للعملاء اللي سجلوا في المتجر.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }
}
