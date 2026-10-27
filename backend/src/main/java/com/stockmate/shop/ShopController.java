package com.stockmate.shop;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockmate.common.PageResponse;
import com.stockmate.payment.PaymentDtos.CheckoutResponse;
import com.stockmate.payment.PaymentDtos.GuestPayRequest;
import com.stockmate.shop.ShopDtos.CheckoutRequest;
import com.stockmate.shop.ShopDtos.CheckoutResult;
import com.stockmate.shop.ShopDtos.GuestCancelRequest;
import com.stockmate.shop.ShopDtos.OrderView;
import com.stockmate.shop.ShopDtos.Quote;
import com.stockmate.shop.ShopDtos.QuoteRequest;
import com.stockmate.shop.ShopDtos.ShopCategory;
import com.stockmate.shop.ShopDtos.ShopProduct;
import com.stockmate.shop.ShopDtos.TrackRequest;

/**
 * The public shop. No login needed; a customer token, when sent, files the order under the account.
 * المتجر العام. ما يحتاج دخول؛ ولو انرسل رمز عميل، الطلب ينحفظ تحت حسابه.
 */
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    // The sort options the shop offers, so a visitor cannot sort by cost price or stock.
    // خيارات الترتيب اللي يعرضها المتجر، حتى الزائر ما يرتب بسعر التكلفة أو المخزون.
    public enum ShopSort {
        NEWEST(Sort.by(Sort.Direction.DESC, "createdAt", "id")),
        PRICE_ASC(Sort.by("salePrice", "id")),
        PRICE_DESC(Sort.by(Sort.Direction.DESC, "salePrice").and(Sort.by("id"))),
        NAME(Sort.by("name", "id"));

        private final Sort sort;

        ShopSort(Sort sort) {
            this.sort = sort;
        }
    }

    private final ShopService shop;

    public ShopController(ShopService shop) {
        this.shop = shop;
    }

    @GetMapping("/products")
    public PageResponse<ShopProduct> products(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(defaultValue = "") String q,
                                              @RequestParam(defaultValue = "NEWEST") ShopSort sort,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        return shop.products(categoryId, q,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), sort.sort));
    }

    @GetMapping("/products/{id}")
    public ShopProduct product(@PathVariable Long id) {
        return shop.product(id);
    }

    @GetMapping("/categories")
    public List<ShopCategory> categories() {
        return shop.categories();
    }

    @PostMapping("/quote")
    public Quote quote(@Valid @RequestBody QuoteRequest body) {
        return shop.quote(body.items());
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResult checkout(@Valid @RequestBody CheckoutRequest body) {
        return shop.checkout(body);
    }

    // POST, not GET: the phone must not end up in server logs or browser history. / POST مو GET: الجوال ما لازم يوصل للسجلات.
    @PostMapping("/orders/track")
    public OrderView track(@Valid @RequestBody TrackRequest body) {
        return shop.track(body.number(), body.phone());
    }

    @PostMapping("/orders/{number}/pay")
    public CheckoutResponse pay(@PathVariable String number, @Valid @RequestBody GuestPayRequest body) {
        return shop.payAsGuest(number, body.phone(), body.method());
    }

    @PostMapping("/orders/{number}/cancel")
    public OrderView cancel(@PathVariable String number, @Valid @RequestBody GuestCancelRequest body) {
        return shop.cancelAsGuest(number, body.phone());
    }
}
