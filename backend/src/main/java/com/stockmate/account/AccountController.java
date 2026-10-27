package com.stockmate.account;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockmate.account.AccountDtos.PasswordChange;
import com.stockmate.account.AccountDtos.ProfileRequest;
import com.stockmate.payment.PaymentDtos.CheckoutResponse;
import com.stockmate.payment.PaymentDtos.PayRequest;
import com.stockmate.shop.ShopDtos.OrderRow;
import com.stockmate.shop.ShopDtos.OrderView;
import com.stockmate.user.UserDtos.UserResponse;

/**
 * Customer endpoints (role CUSTOMER, see SecurityConfig).
 * روابط العميل (دور CUSTOMER، شوف SecurityConfig).
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService account;

    public AccountController(AccountService account) {
        this.account = account;
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@Valid @RequestBody ProfileRequest body) {
        return account.updateProfile(body);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChange body) {
        account.changePassword(body);
    }

    @GetMapping("/orders")
    public List<OrderRow> orders() {
        return account.orders();
    }

    @GetMapping("/orders/{number}")
    public OrderView order(@PathVariable String number) {
        return account.order(number);
    }

    @PostMapping("/orders/{number}/pay")
    public CheckoutResponse pay(@PathVariable String number, @Valid @RequestBody PayRequest body) {
        return account.pay(number, body.method());
    }

    @PostMapping("/orders/{number}/cancel")
    public OrderView cancel(@PathVariable String number) {
        return account.cancel(number);
    }
}
