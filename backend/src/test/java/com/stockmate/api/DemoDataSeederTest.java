package com.stockmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.stockmate.catalog.CategoryService;
import com.stockmate.config.AppProperties;
import com.stockmate.config.DemoDataSeeder;
import com.stockmate.partner.PartnerService;
import com.stockmate.payment.PaymentGateways;
import com.stockmate.payment.PaymentService;
import com.stockmate.product.ProductRepository;
import com.stockmate.product.ProductService;
import com.stockmate.purchase.PurchaseService;
import com.stockmate.sales.SalesService;
import com.stockmate.shop.ShopService;
import com.stockmate.support.IntegrationTest;
import com.stockmate.user.AppUserRepository;

/**
 * The demo data goes through the real services, so it must obey the same rules as user data.
 * البيانات التجريبية تمر عبر نفس الخدمات الحقيقية، فلازم تلتزم بنفس قواعد بيانات المستخدم.
 */
class DemoDataSeederTest extends IntegrationTest {

    @Autowired AppProperties properties;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryService categories;
    @Autowired PartnerService partners;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseService purchases;
    @Autowired SalesService sales;
    @Autowired TransactionTemplate tx;
    @Autowired ShopService shop;
    @Autowired PaymentService paymentService;
    @Autowired PaymentGateways gateways;

    @Test
    void seedProducesAConsistentChronologicalLedger() throws Exception {
        // The base class created test users; the seeder only runs on an empty user table.
        // الكلاس الأساسي أنشأ مستخدمين للاختبار؛ والـ seeder يشتغل فقط لما يكون جدول المستخدمين فاضي.
        jdbc.execute("truncate table app_user restart identity cascade");
        AppProperties enabled = new AppProperties(properties.jwt(), properties.cors(), properties.vatRate(),
                properties.timeZone(), new AppProperties.Seed(true, "Admin@12345", "Staff@12345", "Customer@12345"),
                properties.rateLimit(), properties.payments(), properties.shop());
        seeder(enabled).run(null);

        // Every movement's balance equals the running total in date order (no sale before its stock arrived).
        // رصيد كل حركة يساوي المجموع التراكمي بترتيب التاريخ (ما في بيع قبل وصول مخزونه).
        Integer outOfOrder = jdbc.queryForObject("""
                select count(*) from (
                  select balance_after, sum(quantity) over (partition by product_id order by created_at, id) running
                  from stock_movement) r
                where balance_after <> running
                """, Integer.class);
        assertThat(outOfOrder).isZero();

        // The product's stored quantity matches its latest ledger balance.
        // الكمية المحفوظة للمنتج تطابق آخر رصيد في السجل.
        Integer mismatched = jdbc.queryForObject("""
                select count(*) from product p
                where p.quantity <> coalesce((select m.balance_after from stock_movement m where m.product_id = p.id
                                              order by m.created_at desc, m.id desc limit 1), 0)
                """, Integer.class);
        assertThat(mismatched).isZero();

        assertThat(jdbc.queryForObject("select count(*) from sales_order where status = 'CONFIRMED' and channel = 'OFFICE'",
                Integer.class)).isEqualTo(26);
        // Shop orders in every state, with real payments behind the paid ones.
        // طلبات متجر بكل الحالات، والمدفوعة وراها دفعات حقيقية.
        assertThat(jdbc.queryForList("select fulfilment from sales_order where channel = 'ONLINE' and status = 'CONFIRMED'",
                String.class)).containsExactlyInAnyOrder("DELIVERED", "DELIVERED", "SHIPPED", "NEW", "NEW", "NEW");
        assertThat(jdbc.queryForObject("select count(*) from sales_order where channel = 'ONLINE' and status = 'CANCELLED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select method from payment where status = 'PAID'", String.class))
                .containsExactlyInAnyOrder("CARD", "CARD", "TABBY", "TAMARA", "CASH");
        assertThat(jdbc.queryForObject("""
                select count(*) from sales_order o where o.channel = 'ONLINE' and o.status = 'CONFIRMED'
                  and o.payment_status = 'UNPAID' and o.payment_choice <> 'CASH'""", Integer.class)).isZero();
        // The demo customer signs in with the phone number, typed the local way.
        // العميل التجريبي يدخل برقم الجوال، مكتوب بالطريقة المحلية.
        String token = login("0553001010", "Customer@12345");
        mvc.perform(get("/api/account/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        // Running again must not duplicate anything.
        // التشغيل مرة ثانية لازم ما يكرر أي شي.
        seeder(enabled).run(null);
        assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(16);
    }

    private DemoDataSeeder seeder(AppProperties enabled) {
        return new DemoDataSeeder(enabled, users, passwordEncoder, categories, partners, productService,
                productRepository, purchases, sales, jdbc, tx, shop, paymentService, gateways);
    }
}
