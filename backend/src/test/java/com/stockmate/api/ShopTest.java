package com.stockmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockmate.shop.ShopService;
import com.stockmate.support.IntegrationTest;

/**
 * The online shop: what visitors see, checkout with each way of paying, tracking, cancelling and the payment timeout.
 * المتجر الإلكتروني: وش يشوف الزوار، والشراء بكل طرق الدفع، والتتبع، والإلغاء، وانتهاء مهلة الدفع.
 */
class ShopTest extends IntegrationTest {

    @Autowired
    ShopService shop;

    String admin;
    long rice;
    long dates;

    @BeforeEach
    void catalogue() throws Exception {
        admin = adminToken();
        long food = category(admin, "Food");
        // 40.00 + 15% VAT = 46.00 in the shop. / 40.00 + 15% ضريبة = 46.00 في المتجر.
        rice = shopProduct(admin, "RICE", food, "40.00", 10);
        dates = shopProduct(admin, "DATES", food, "100.00", 3);
    }

    private String items(long productId, int quantity) {
        return "[{\"productId\":%d,\"quantity\":%d}]".formatted(productId, quantity);
    }

    private String checkout(String body) throws Exception {
        return mvc.perform(json(post("/api/shop/checkout")).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void visitorsSeeOnlyPublishedActiveProductsWithVatPricesAndNoCost() throws Exception {
        long food = jdbc.queryForObject("select id from category where name = 'Food'", Long.class);
        product(admin, "OFFICE-ONLY", food, "5.00", "9.00", 0, 50);
        jdbc.update("update product set active = false where id = ?", dates);
        mvc.perform(get("/api/shop/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("RICE"))
                .andExpect(jsonPath("$.content[0].nameAr").value("منتج RICE"))
                .andExpect(jsonPath("$.content[0].price").value(46.00))
                .andExpect(jsonPath("$.content[0].inStock").value(true))
                // Plenty in stock: the exact count is not shown. / المخزون كثير: العدد بالضبط ما ينعرض.
                .andExpect(jsonPath("$.content[0].left").doesNotExist())
                .andExpect(jsonPath("$.content[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.content[0].quantity").doesNotExist());
        mvc.perform(get("/api/shop/products/" + dates)).andExpect(status().isNotFound());
        mvc.perform(get("/api/shop/products?q=ric")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/shop/products?q=منتج")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/shop/categories"))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].productCount").value(1));
    }

    @Test
    void quoteUsesServerPricesAndFlagsShortStock() throws Exception {
        mvc.perform(json(post("/api/shop/quote")).content("""
                        {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":5}]}""".formatted(rice, dates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(580.00))
                .andExpect(jsonPath("$.vat").value(87.00))
                .andExpect(jsonPath("$.total").value(667.00))
                .andExpect(jsonPath("$.canCheckout").value(false))
                .andExpect(jsonPath("$.lines[1].problem").value("STOCK"))
                .andExpect(jsonPath("$.lines[1].available").value(3));
    }

    @Test
    void cashOnDeliveryOrderTakesStockAndShowsInTheOffice() throws Exception {
        String result = checkout(checkoutJson(items(rice, 2), "055 111 2233", "CASH"));
        String number = JsonPath.read(result, "$.number");
        assertThat((Object) JsonPath.read(result, "$.checkoutUrl")).isNull();
        assertThat(new BigDecimal(JsonPath.read(result, "$.total").toString())).isEqualByComparingTo("92.00");
        assertThat(quantityOf(rice)).isEqualTo(8);

        // A second order with the same phone reuses the customer record. / طلب ثاني بنفس الجوال يستخدم نفس سجل العميل.
        checkout(checkoutJson(items(rice, 1), "+966551112233", "CASH"));
        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select phone from customer", String.class)).isEqualTo("+966 55 111 2233");

        mvc.perform(auth(get("/api/sales-orders?channel=ONLINE"), staffToken()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].number").value(number))
                .andExpect(jsonPath("$.content[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[1].fulfilment").value("NEW"))
                .andExpect(jsonPath("$.content[1].paymentChoice").value("CASH"));
    }

    @Test
    void cardCheckoutIsPaidOnTheSandboxPage() throws Exception {
        String result = checkout(checkoutJson(items(dates, 1), "0551112233", "CARD"));
        String number = JsonPath.read(result, "$.number");
        String paymentId = JsonPath.read(result, "$.paymentId");
        assertThat((String) JsonPath.read(result, "$.checkoutUrl")).isEqualTo("http://localhost:4200/pay/" + paymentId);
        assertThat((Object) JsonPath.read(result, "$.payBy")).isNotNull();
        assertThat(quantityOf(dates)).isEqualTo(2);

        mvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(jsonPath("$.orderNumber").value(number))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.amount").value(115.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
        // A declined card leaves the attempt open for another card. / البطاقة المرفوضة تخلي المحاولة مفتوحة.
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox"))
                        .content(TEST_CARD.replace("4111 1111 1111 1111", "4000000000000002")))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("CARD_DECLINED"));
        assertThat(paymentStatusOf(number)).isEqualTo("UNPAID");
        // Paying again reuses the same order; a new attempt is made because the last one failed.
        // الدفع مرة ثانية على نفس الطلب؛ تنعمل محاولة جديدة لأن الأخيرة فشلت.
        String again = mvc.perform(json(post("/api/shop/orders/" + number + "/pay"))
                        .content("{\"phone\":\"0551112233\",\"method\":\"CARD\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(again, "$.paymentId");
        mvc.perform(json(post("/api/public/payments/" + second + "/sandbox")).content(TEST_CARD))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.cardBrand").value("visa"))
                .andExpect(jsonPath("$.cardLast4").value("1111"));
        assertThat(paymentStatusOf(number)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select pay_by from sales_order where number = ?", Object.class, number)).isNull();

        mvc.perform(json(post("/api/shop/orders/track")).content(
                        "{\"number\":\"%s\",\"phone\":\"+966 55 111 2233\"}".formatted(number.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.payable").value(false))
                .andExpect(jsonPath("$.lines[0].sku").value("DATES"))
                .andExpect(jsonPath("$.shipTo.district").value("Al Sahafa"))
                .andExpect(jsonPath("$.cancellable").value(true));
    }

    @Test
    void trackingNeedsTheRightPhone() throws Exception {
        String number = JsonPath.read(checkout(checkoutJson(items(rice, 1), "0551112233", "CASH")), "$.number");
        mvc.perform(json(post("/api/shop/orders/track")).content(
                        "{\"number\":\"%s\",\"phone\":\"0559999999\"}".formatted(number)))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/shop/orders/" + number + "/cancel")).content("{\"phone\":\"0559999999\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void instalmentsOnlyWithinTheirLimits() throws Exception {
        // 46.00 is below the 100 SAR minimum: refused, and nothing is kept. / 46.00 أقل من الحد الأدنى: مرفوض وما ينحفظ شي.
        mvc.perform(json(post("/api/shop/checkout")).content(checkoutJson(items(rice, 1), "0551112233", "TABBY")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALMENTS_NOT_AVAILABLE"));
        assertThat(quantityOf(rice)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from sales_order", Integer.class)).isZero();

        String result = checkout(checkoutJson(items(rice, 3), "0551112233", "TAMARA"));
        String paymentId = JsonPath.read(result, "$.paymentId");
        mvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(jsonPath("$.instalments.length()").value(4))
                .andExpect(jsonPath("$.instalments[0].amount").value(34.50));
        mvc.perform(json(post("/api/public/payments/" + paymentId + "/sandbox")).content("{\"approve\":true}"))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void checkoutIsRefusedForUnavailableOrMissingStockAndKeepsNothing() throws Exception {
        jdbc.update("update product set published = false where id = ?", dates);
        mvc.perform(json(post("/api/shop/checkout")).content(checkoutJson(items(dates, 1), "0551112233", "CASH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.productId").value(dates));
        mvc.perform(json(post("/api/shop/checkout")).content(checkoutJson(items(rice, 11), "0551112233", "CASH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.available").value(10));
        mvc.perform(json(post("/api/shop/checkout")).content(checkoutJson(items(rice, 1), "12345", "CASH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").value("Phone"));
        assertThat(quantityOf(rice)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from sales_order", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isZero();
    }

    @Test
    void aFilledHoneypotLooksLikeSuccessButSavesNothing() throws Exception {
        String body = checkoutJson(items(rice, 1), "0551112233", "CASH").replace("\"paymentMethod\"",
                "\"website\":\"http://spam.example\",\"paymentMethod\"");
        checkout(body);
        assertThat(jdbc.queryForObject("select count(*) from sales_order", Integer.class)).isZero();
        assertThat(quantityOf(rice)).isEqualTo(10);
    }

    @Test
    void guestCancelsAPaidOrderAndGetsRefundedAndStockReturns() throws Exception {
        String result = checkout(checkoutJson(items(dates, 2), "0551112233", "CARD"));
        String number = JsonPath.read(result, "$.number");
        mvc.perform(json(post("/api/public/payments/" + JsonPath.read(result, "$.paymentId") + "/sandbox"))
                .content(TEST_CARD)).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(quantityOf(dates)).isEqualTo(1);

        mvc.perform(json(post("/api/shop/orders/" + number + "/cancel")).content("{\"phone\":\"0551112233\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("CUSTOMER"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
        assertThat(quantityOf(dates)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select status from payment where status <> 'FAILED'", String.class))
                .isEqualTo("REFUNDED");
        // Cancelling twice is refused. / الإلغاء مرتين مرفوض.
        mvc.perform(json(post("/api/shop/orders/" + number + "/cancel")).content("{\"phone\":\"0551112233\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unpaidOnlineOrdersAreCancelledWhenTheirTimeRunsOut() throws Exception {
        String unpaid = JsonPath.read(checkout(checkoutJson(items(rice, 4), "0551112233", "CARD")), "$.number");
        String paidResult = checkout(checkoutJson(items(rice, 1), "0551112244", "CARD"));
        String paid = JsonPath.read(paidResult, "$.number");
        mvc.perform(json(post("/api/public/payments/" + JsonPath.read(paidResult, "$.paymentId") + "/sandbox"))
                .content(TEST_CARD)).andExpect(jsonPath("$.status").value("PAID"));
        String cod = JsonPath.read(checkout(checkoutJson(items(rice, 1), "0551112255", "CASH")), "$.number");
        assertThat(quantityOf(rice)).isEqualTo(4);

        // Not yet: the time has not run out. / مو بعد: المهلة ما خلصت.
        assertThat(shop.expireUnpaid()).isZero();
        jdbc.update("update sales_order set pay_by = now() - interval '1 minute' where number = ?", unpaid);
        assertThat(shop.expireUnpaid()).isEqualTo(1);

        assertThat(jdbc.queryForObject("select status from sales_order where number = ?", String.class, unpaid))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancel_reason from sales_order where number = ?", String.class, unpaid))
                .isEqualTo("PAYMENT_TIMEOUT");
        assertThat(jdbc.queryForObject("select p.status from payment p join sales_order o on o.id = p.order_id"
                + " where o.number = ?", String.class, unpaid)).isEqualTo("FAILED");
        assertThat(quantityOf(rice)).isEqualTo(8);
        // Paid and cash-on-delivery orders are never touched. / المدفوع والدفع عند الاستلام ما ينلمسون.
        assertThat(jdbc.queryForList("select status from sales_order where number in (?, ?)", String.class, paid, cod))
                .containsOnly("CONFIRMED");
        assertThat(shop.expireUnpaid()).isZero();
    }

    @Test
    void theWarehouseShipsAndDeliversWithTheRightPaymentRules() throws Exception {
        String staff = staffToken();
        long card = orderId(JsonPath.read(checkout(checkoutJson(items(rice, 1), "0551112233", "CARD")), "$.number"));
        long cod = orderId(JsonPath.read(checkout(checkoutJson(items(rice, 1), "0551112244", "CASH")), "$.number"));

        // An unpaid card order cannot leave the warehouse. / طلب البطاقة اللي ما انْدفع ما يطلع من المستودع.
        mvc.perform(auth(post("/api/sales-orders/" + card + "/ship"), staff))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
        // Office edits are refused for shop orders. / تعديل المكتب مرفوض لطلبات المتجر.
        mvc.perform(auth(put("/api/sales-orders/" + cod), staff).content("""
                        {"customerId":1,"lines":[{"productId":%d,"quantity":1}]}""".formatted(rice)))
                .andExpect(status().isConflict());

        mvc.perform(auth(post("/api/sales-orders/" + cod + "/ship"), staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfilment").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt").isNotEmpty());
        mvc.perform(auth(post("/api/sales-orders/" + cod + "/deliver"), staff).content("{\"cashCollected\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cashCollected").value("CashRequired"));
        mvc.perform(auth(post("/api/sales-orders/" + cod + "/deliver"), staff).content("{\"cashCollected\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfilment").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payments[0].method").value("CASH"))
                .andExpect(jsonPath("$.payments[0].recordedBy").value("staff"));
        // A delivered order is finished. / الطلب اللي توصّل خلص.
        mvc.perform(auth(post("/api/sales-orders/" + cod + "/cancel"), staff))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // A bank transfer recorded by the office makes the card order shippable. / تحويل بنكي يسجله المكتب.
        mvc.perform(auth(post("/api/sales-orders/" + card + "/cash"), staff))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mvc.perform(auth(post("/api/sales-orders/" + card + "/ship"), staff)).andExpect(status().isOk());
        // The customer can no longer cancel a shipped order. / العميل ما يقدر يلغي طلب انشحن.
        String number = jdbc.queryForObject("select number from sales_order where id = ?", String.class, card);
        mvc.perform(json(post("/api/shop/orders/" + number + "/cancel")).content("{\"phone\":\"0551112233\"}"))
                .andExpect(status().isConflict());
        // The office can, and the money and the stock come back. / المكتب يقدر، وترجع الفلوس والمخزون.
        mvc.perform(auth(post("/api/sales-orders/" + card + "/cancel"), staff).content("{\"reason\":\"Returned\"}"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.cancelReason").value("Returned"));
        assertThat(quantityOf(rice)).isEqualTo(9);
    }

    @Test
    void dashboardCountsTheShop() throws Exception {
        checkout(checkoutJson(items(rice, 1), "0551112244", "CASH"));
        String result = checkout(checkoutJson(items(rice, 1), "0551112233", "CARD"));
        mvc.perform(json(post("/api/public/payments/" + JsonPath.read(result, "$.paymentId") + "/sandbox"))
                .content(TEST_CARD));
        mvc.perform(auth(get("/api/dashboard"), adminToken()))
                .andExpect(jsonPath("$.shop.toShip").value(2))
                .andExpect(jsonPath("$.shop.outForDelivery").value(0))
                .andExpect(jsonPath("$.shop.collectedThisMonth").value(46.00));
    }

    private long orderId(String number) {
        return jdbc.queryForObject("select id from sales_order where number = ?", Long.class, number);
    }
}
