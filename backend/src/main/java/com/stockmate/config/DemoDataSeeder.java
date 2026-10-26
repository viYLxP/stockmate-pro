package com.stockmate.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.stockmate.catalog.CategoryDtos.CategoryRequest;
import com.stockmate.catalog.CategoryService;
import com.stockmate.order.LineRequest;
import com.stockmate.partner.PartnerDtos.CustomerRequest;
import com.stockmate.partner.PartnerDtos.SupplierRequest;
import com.stockmate.partner.PartnerService;
import com.stockmate.product.ProductDtos.CreateProductRequest;
import com.stockmate.product.ProductDtos.ProductResponse;
import com.stockmate.product.ProductRepository;
import com.stockmate.product.ProductService;
import com.stockmate.purchase.PurchaseDtos.PurchaseOrderRequest;
import com.stockmate.purchase.PurchaseService;
import com.stockmate.sales.SalesDtos.SalesOrderRequest;
import com.stockmate.sales.SalesDtos.SalesOrderResponse;
import com.stockmate.sales.SalesService;
import com.stockmate.user.AppUser;
import com.stockmate.user.AppUserRepository;
import com.stockmate.user.Role;

/**
 * Fills an EMPTY database with demo users and realistic sample data on first start.
 * يعبّي قاعدة بيانات فاضية بمستخدمين تجريبيين وبيانات واقعية عند أول تشغيل.
 *
 * <p>It goes through the real services (not raw SQL inserts), so the demo data obeys the same business rules
 * as data entered by users: stock comes from movements, totals include VAT, and so on.
 * It never runs when users already exist, and can be turned off with SEED_DEMO_DATA=false.</p>
 * <p>يستخدم الخدمات الحقيقية (مو إدخال SQL مباشر)، فالبيانات التجريبية تخضع لنفس قواعد العمل:
 * المخزون يجي من الحركات، والإجماليات فيها ضريبة، وهكذا. وما يشتغل أبدًا لو فيه مستخدمين،
 * ويمكن إيقافه بـ SEED_DEMO_DATA=false.</p>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categories;
    private final PartnerService partners;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final PurchaseService purchases;
    private final SalesService sales;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder,
                          CategoryService categories, PartnerService partners, ProductService productService,
                          ProductRepository productRepository, PurchaseService purchases, SalesService sales,
                          JdbcTemplate jdbc, TransactionTemplate tx) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.categories = categories;
        this.partners = partners;
        this.productService = productService;
        this.productRepository = productRepository;
        this.purchases = purchases;
        this.sales = sales;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled() || users.count() > 0) {
            return;
        }
        // One transaction for the whole seed: either all demo data is created, or none (no half-filled database).
        // معاملة وحدة لكل البيانات: يا تنضاف كلها يا ولا شي (ما تبقى قاعدة بيانات نصها معبّى).
        tx.executeWithoutResult(status -> seed());
        log.info("Demo data created. Log in as 'admin' or 'staff' (passwords from ADMIN_PASSWORD / STAFF_PASSWORD).");
    }

    private void seed() {
        users.save(new AppUser("admin", "System Admin", passwordEncoder.encode(properties.seed().adminPassword()), Role.ADMIN));
        users.save(new AppUser("staff", "Warehouse Staff", passwordEncoder.encode(properties.seed().staffPassword()), Role.STAFF));

        long beverages = categories.create(new CategoryRequest("Beverages", "Water, juices and soft drinks")).id();
        long groceries = categories.create(new CategoryRequest("Groceries", "Rice, oil, sugar and dry food")).id();
        long cleaning = categories.create(new CategoryRequest("Cleaning", "Household cleaning supplies")).id();
        long stationery = categories.create(new CategoryRequest("Stationery", "Office and school supplies")).id();
        long electronics = categories.create(new CategoryRequest("Electronics", "Small electronics and accessories")).id();

        long sup1 = partners.createSupplier(new SupplierRequest("Al Noor Trading Co.", "+966 11 400 1122", "sales@alnoor.example", "Riyadh", "300123456700003")).id();
        long sup2 = partners.createSupplier(new SupplierRequest("Red Sea Distribution", "+966 12 610 3344", "orders@redsea.example", "Jeddah", "310987654300003")).id();
        long sup3 = partners.createSupplier(new SupplierRequest("Eastern Supplies Est.", "+966 13 820 5566", "info@eastern.example", "Dammam", null)).id();

        List<Long> customerIds = List.of(
                partners.createCustomer(new CustomerRequest("Al Waha Mini Market", "+966 50 111 2233", "waha@example.com", "Riyadh")).id(),
                partners.createCustomer(new CustomerRequest("Najd Office Services", "+966 55 444 5566", "najd@example.com", "Riyadh")).id(),
                partners.createCustomer(new CustomerRequest("Corniche Cafe", "+966 54 777 8899", "corniche@example.com", "Jeddah")).id(),
                partners.createCustomer(new CustomerRequest("Gulf Cleaning Co.", "+966 56 222 3344", "gulfclean@example.com", "Khobar")).id(),
                partners.createCustomer(new CustomerRequest("Taiba School Store", "+966 59 666 1100", "taiba@example.com", "Madinah")).id());

        // sku, name, category, supplier, unit, cost, price, reorder level, opening quantity
        // رمز الصنف، الاسم، التصنيف، المورد، الوحدة، التكلفة، السعر، حد إعادة الطلب، الكمية الافتتاحية
        List<ProductResponse> created = new ArrayList<>();
        created.add(product("BEV-001", "Mineral Water 330ml (40 pack)", beverages, sup2, "carton", "14.50", "19.95", 20, 120));
        created.add(product("BEV-002", "Orange Juice 1L", beverages, sup2, "bottle", "4.20", "6.50", 30, 80));
        created.add(product("BEV-003", "Arabic Coffee 500g", beverages, sup1, "pack", "28.00", "39.00", 15, 40));
        created.add(product("BEV-004", "Green Tea (100 bags)", beverages, sup1, "box", "11.00", "16.50", 10, 8));
        created.add(product("GRO-001", "Basmati Rice 5kg", groceries, sup1, "bag", "32.00", "44.95", 25, 90));
        created.add(product("GRO-002", "Sunflower Oil 1.8L", groceries, sup1, "bottle", "15.75", "21.50", 20, 60));
        created.add(product("GRO-003", "White Sugar 2kg", groceries, sup3, "bag", "6.40", "8.95", 25, 18));
        created.add(product("GRO-004", "Dates Sukkari 1kg", groceries, sup3, "box", "22.00", "34.00", 12, 45));
        created.add(product("CLN-001", "Dishwashing Liquid 1L", cleaning, sup3, "bottle", "5.10", "7.95", 20, 70));
        created.add(product("CLN-002", "Laundry Detergent 3kg", cleaning, sup3, "bag", "24.00", "33.50", 10, 0));
        created.add(product("CLN-003", "Floor Cleaner 2L", cleaning, sup3, "bottle", "8.30", "12.95", 15, 35));
        created.add(product("STA-001", "A4 Copy Paper (500 sheets)", stationery, sup2, "ream", "13.00", "18.50", 40, 150));
        created.add(product("STA-002", "Blue Ballpoint Pens (50)", stationery, sup2, "box", "17.50", "25.00", 10, 12));
        created.add(product("ELC-001", "USB-C Charger 20W", electronics, sup1, "piece", "29.00", "49.00", 8, 25));
        created.add(product("ELC-002", "Wireless Mouse", electronics, sup1, "piece", "35.00", "59.00", 6, 5));
        created.add(product("ELC-003", "HDMI Cable 2m", electronics, sup2, "piece", "9.50", "19.00", 10, 40));

        // Two received purchase orders (stock in + weighted cost update) and one draft awaiting delivery.
        // طلبي شراء مستلمين (دخول مخزون وتحديث التكلفة) وطلب مسودة ينتظر التوصيل.
        Long po1 = purchases.create(new PurchaseOrderRequest(sup1, "Monthly restock", List.of(
                new LineRequest(id(created, "GRO-001"), 40, new BigDecimal("31.50")),
                new LineRequest(id(created, "BEV-003"), 20, null)))).id();
        purchases.receive(po1);
        Long po2 = purchases.create(new PurchaseOrderRequest(sup2, null, List.of(
                new LineRequest(id(created, "STA-001"), 60, null),
                new LineRequest(id(created, "BEV-001"), 50, new BigDecimal("14.00"))))).id();
        purchases.receive(po2);
        purchases.create(new PurchaseOrderRequest(sup3, "Urgent: detergent is out of stock", List.of(
                new LineRequest(id(created, "CLN-002"), 30, null),
                new LineRequest(id(created, "GRO-003"), 50, null))));

        // Confirmed sales spread over the last 14 days so the dashboard chart has history.
        // A fixed random seed (42) makes the demo identical on every fresh install.
        // مبيعات مؤكدة موزعة على آخر 14 يوم حتى يكون في رسم لوحة التحكم سجل.
        // بذرة عشوائية ثابتة (42) تخلي البيانات التجريبية نفسها في كل تثبيت جديد.
        Random random = new Random(42);
        List<String> sellable = List.of("BEV-001", "BEV-002", "BEV-003", "GRO-001", "GRO-002", "GRO-004",
                "CLN-001", "CLN-003", "STA-001", "ELC-001", "ELC-003");
        Instant now = Instant.now();
        for (int i = 0; i < 26; i++) {
            List<LineRequest> lines = new ArrayList<>();
            int lineCount = 1 + random.nextInt(3);
            List<String> pool = new ArrayList<>(sellable);
            for (int l = 0; l < lineCount; l++) {
                // Remove the picked SKU from the pool so a product never appears twice in one order.
                // نشيل الصنف المختار من القائمة حتى ما يتكرر المنتج في نفس الطلب.
                String sku = pool.remove(random.nextInt(pool.size()));
                lines.add(new LineRequest(id(created, sku), 1 + random.nextInt(5), null));
            }
            SalesOrderResponse order = sales.create(new SalesOrderRequest(
                    customerIds.get(random.nextInt(customerIds.size())), null, lines));
            sales.confirm(order.id());
            // Spread the order over the last 14 days (0..13 days ago, during business hours).
            // نوزّع الطلب على آخر 14 يوم (من 0 إلى 13 يوم مضى، خلال ساعات العمل).
            Instant when = now.minus(Duration.ofDays(random.nextInt(14))).minus(Duration.ofHours(random.nextInt(6)));
            backdate(order.number(), when);
        }
        // One draft and one cancelled order to show every status in the UI.
        // مسودة وطلب ملغي حتى تظهر كل الحالات في الواجهة.
        sales.create(new SalesOrderRequest(customerIds.get(1), "Waiting for customer approval",
                List.of(new LineRequest(id(created, "STA-001"), 20, new BigDecimal("17.50")))));
        SalesOrderResponse cancelled = sales.create(new SalesOrderRequest(customerIds.get(2), null,
                List.of(new LineRequest(id(created, "BEV-002"), 6, null))));
        sales.confirm(cancelled.id());
        sales.cancel(cancelled.id());
    }

    private ProductResponse product(String sku, String name, long categoryId, long supplierId, String unit,
                                    String cost, String price, int reorder, int opening) {
        return productService.create(new CreateProductRequest(sku, name, categoryId, supplierId, unit,
                new BigDecimal(cost), new BigDecimal(price), reorder, opening));
    }

    private static Long id(List<ProductResponse> products, String sku) {
        return products.stream().filter(p -> p.sku().equals(sku)).findFirst().orElseThrow().id();
    }

    /**
     * Moves a demo order (and its stock movements) back in time. Only used for demo data;
     * real orders always keep the time they were actually created.
     * يرجّع تاريخ طلب تجريبي (وحركاته) للماضي. يُستخدم للبيانات التجريبية فقط؛
     * الطلبات الحقيقية تحتفظ دائمًا بوقت إنشائها الفعلي.
     */
    private void backdate(String orderNumber, Instant when) {
        // Write pending JPA changes first. Otherwise Hibernate would flush the order at commit time and
        // overwrite the confirmed_at value we set below with plain SQL.
        // نكتب تغييرات JPA المعلّقة أولًا. وإلا Hibernate بيحفظ الطلب عند نهاية المعاملة
        // ويكتب فوق قيمة confirmed_at اللي نعدّلها تحت بـ SQL مباشر.
        productRepository.flush();
        java.sql.Timestamp ts = java.sql.Timestamp.from(when);
        jdbc.update("update sales_order set created_at = ?, confirmed_at = ? where number = ?", ts, ts, orderNumber);
        jdbc.update("update stock_movement set created_at = ? where reference = ?", ts, orderNumber);
    }
}
