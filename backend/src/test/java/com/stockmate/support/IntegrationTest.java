package com.stockmate.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stockmate.user.AppUser;
import com.stockmate.user.AppUserRepository;
import com.stockmate.user.Role;

/**
 * Base class for integration tests: real Spring context + a REAL PostgreSQL in Docker (Testcontainers).
 * الكلاس الأساسي لاختبارات التكامل: سياق Spring حقيقي + PostgreSQL حقيقي داخل Docker (Testcontainers).
 *
 * <p>We test against PostgreSQL instead of an in-memory database so the Flyway migrations, constraints,
 * sequences and row locks are exactly the ones used in production.</p>
 * <p>نختبر على PostgreSQL بدل قاعدة بيانات في الذاكرة حتى تكون ترحيلات Flyway والقيود والـ sequences
 * وأقفال الصفوف هي نفسها اللي في الإنتاج.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    // One container shared by all test classes (started once), which keeps the suite fast.
    // حاوية وحدة مشتركة بين كل كلاسات الاختبار (تشتغل مرة وحدة)، وهذا يخلي الاختبارات سريعة.
    // @ServiceConnection points spring.datasource at this container automatically.
    // @ServiceConnection يوجّه إعدادات قاعدة البيانات لهذي الحاوية تلقائيًا.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String STAFF_PASSWORD = "Staff@12345";
    protected static final String CUSTOMER_PASSWORD = "Customer@12345";
    // A test card the built-in sandbox approves (Visa, passes the Luhn check). / بطاقة تجريبية يوافق عليها الوضع التجريبي.
    protected static final String TEST_CARD = """
            {"cardNumber":"4111 1111 1111 1111","expMonth":12,"expYear":2099,"cvc":"123","cardHolder":"Test Buyer"}""";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Wipes all business data before each test so tests never depend on each other's leftovers.
     * يمسح كل البيانات قبل كل اختبار حتى ما يعتمد أي اختبار على بقايا اختبار ثاني.
     */
    @BeforeEach
    void resetDatabase() {
        // RESTART IDENTITY also resets ids and RESTART on the sequences makes order numbers predictable (PO-000001).
        // RESTART IDENTITY يصفّر الأرقام، وإعادة تشغيل الـ sequences تخلي أرقام الطلبات متوقعة (PO-000001).
        jdbc.execute("""
                truncate table payment, stock_movement, sales_order_line, sales_order, purchase_order_line, purchase_order,
                product, customer, supplier, category, app_user restart identity cascade
                """);
        jdbc.execute("alter sequence purchase_order_number_seq restart with 1");
        jdbc.execute("alter sequence sales_order_number_seq restart with 1");
        users.save(new AppUser("admin", "Admin", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN));
        users.save(new AppUser("staff", "Staff", passwordEncoder.encode(STAFF_PASSWORD), Role.STAFF));
    }

    /**
     * Logs in through the real endpoint and returns a JWT.
     * يسجّل دخول عبر الرابط الحقيقي ويرجّع رمز JWT.
     */
    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected String adminToken() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected String staffToken() throws Exception {
        return login("staff", STAFF_PASSWORD);
    }

    /**
     * Adds the bearer token and JSON content type to a request.
     * يضيف رمز الدخول ونوع المحتوى JSON للطلب.
     */
    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Performs a POST that must succeed and returns the "id" field of the response.
     * ينفذ POST لازم ينجح ويرجّع حقل "id" من الرد.
     */
    protected long createId(String url, String json, String token) throws Exception {
        String body = mvc.perform(auth(post(url), token).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ----- Fixture helpers / دوال تجهيز البيانات -----

    protected long category(String token, String name) throws Exception {
        return createId("/api/categories", "{\"name\":\"%s\"}".formatted(name), token);
    }

    protected long supplier(String token, String name) throws Exception {
        return createId("/api/suppliers", "{\"name\":\"%s\"}".formatted(name), token);
    }

    protected long customer(String token, String name) throws Exception {
        return createId("/api/customers", "{\"name\":\"%s\"}".formatted(name), token);
    }

    protected long product(String token, String sku, long categoryId, String cost, String price,
                           int reorderLevel, int openingQuantity) throws Exception {
        return createId("/api/products", """
                {"sku":"%s","name":"Product %s","categoryId":%d,"unit":"piece","costPrice":%s,"salePrice":%s,
                 "reorderLevel":%d,"openingQuantity":%d}
                """.formatted(sku, sku, categoryId, cost, price, reorderLevel, openingQuantity), token);
    }

    // A product shown in the online shop. / منتج معروض في المتجر الإلكتروني.
    protected long shopProduct(String token, String sku, long categoryId, String price, int openingQuantity)
            throws Exception {
        return createId("/api/products", """
                {"sku":"%s","name":"Product %s","categoryId":%d,"unit":"piece","costPrice":1.00,"salePrice":%s,
                 "reorderLevel":0,"openingQuantity":%d,
                 "shop":{"published":true,"nameAr":"منتج %s","description":"Nice","descriptionAr":"حلو"}}
                """.formatted(sku, sku, categoryId, price, openingQuantity, sku), token);
    }

    // Signs up a shop customer and returns their token. / يسجّل عميل متجر ويرجّع رمزه.
    protected String registerCustomer(String name, String phone) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","phone":"%s","email":null,"password":"%s"}
                                """.formatted(name, phone, CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /**
     * A shop checkout body for a guest. items is the raw JSON array, e.g. [{"productId":1,"quantity":2}].
     * جسم طلب شراء من المتجر لزائر. items مصفوفة JSON جاهزة.
     */
    protected static String checkoutJson(String items, String phone, String method) {
        return """
                {"items":%s,"fullName":"Reem Al Qahtani","phone":"%s","email":"reem@example.com","city":"RIYADH",
                 "district":"Al Sahafa","address":"Street 5, house 12","paymentMethod":"%s"}
                """.formatted(items, phone, method);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    protected String paymentStatusOf(String orderNumber) {
        return jdbc.queryForObject("select payment_status from sales_order where number = ?", String.class, orderNumber);
    }

    protected int quantityOf(long productId) {
        return jdbc.queryForObject("select quantity from product where id = ?", Integer.class, productId);
    }

    protected BigDecimal costOf(long productId) {
        return jdbc.queryForObject("select cost_price from product where id = ?", BigDecimal.class, productId);
    }
}
