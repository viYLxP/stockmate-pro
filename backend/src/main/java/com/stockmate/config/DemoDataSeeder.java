package com.stockmate.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
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
import com.stockmate.common.City;
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentDtos.SandboxRequest;
import com.stockmate.payment.PaymentGateways;
import com.stockmate.payment.PaymentService;
import com.stockmate.product.ProductDtos.ShopListing;
import com.stockmate.shop.ShopDtos.CartItem;
import com.stockmate.shop.ShopDtos.CheckoutRequest;
import com.stockmate.shop.ShopDtos.CheckoutResult;
import com.stockmate.shop.ShopService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final ShopService shop;
    private final PaymentService paymentService;
    private final PaymentGateways gateways;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder,
                          CategoryService categories, PartnerService partners, ProductService productService,
                          ProductRepository productRepository, PurchaseService purchases, SalesService sales,
                          JdbcTemplate jdbc, TransactionTemplate tx, ShopService shop, PaymentService paymentService,
                          PaymentGateways gateways) {
        this.shop = shop;
        this.paymentService = paymentService;
        this.gateways = gateways;
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
        log.info("Demo data created. Log in as 'admin' or 'staff', or as shop customer 0553001010 "
                + "(passwords from ADMIN_PASSWORD / STAFF_PASSWORD / CUSTOMER_PASSWORD).");
    }

    private void seed() {
        users.save(new AppUser("admin", "System Admin", passwordEncoder.encode(properties.seed().adminPassword()), Role.ADMIN));
        users.save(new AppUser("staff", "Warehouse Staff", passwordEncoder.encode(properties.seed().staffPassword()), Role.STAFF));

        long beverages = categories.create(new CategoryRequest("Beverages", "مشروبات", "Water, juices and soft drinks")).id();
        long groceries = categories.create(new CategoryRequest("Groceries", "مواد غذائية", "Rice, oil, sugar and dry food")).id();
        long cleaning = categories.create(new CategoryRequest("Cleaning", "منظفات", "Household cleaning supplies")).id();
        long stationery = categories.create(new CategoryRequest("Stationery", "قرطاسية", "Office and school supplies")).id();
        long electronics = categories.create(new CategoryRequest("Electronics", "إلكترونيات", "Small electronics and accessories")).id();

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

        // The demo history runs forward in time like a real shop: opening stock 20 days ago, deliveries 15 days ago,
        // then sales over the last 14 days. That keeps every product's ledger balance in date order.
        // سجل البيانات التجريبية يمشي للأمام بالزمن مثل محل حقيقي: الرصيد الافتتاحي قبل 20 يوم، التوريد قبل 15 يوم،
        // ثم المبيعات خلال آخر 14 يوم. وهذا يخلي رصيد كل منتج في السجل مرتب حسب التاريخ.
        Instant now = Instant.now();
        productRepository.flush();
        jdbc.update("update stock_movement set created_at = ? where type = 'OPENING'",
                java.sql.Timestamp.from(now.minus(Duration.ofDays(20))));

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
        backdatePurchase(po1, now.minus(Duration.ofDays(15)));
        backdatePurchase(po2, now.minus(Duration.ofDays(15)).plus(Duration.ofHours(2)));
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
        // Pick the 26 sale times first and sort them oldest → newest, so orders are created in time order.
        // نختار أوقات الـ 26 بيعة أولًا ونرتبها من الأقدم للأحدث، حتى تنشأ الطلبات بترتيب زمني.
        List<Instant> saleTimes = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            // 0..13 days ago, during the last 6 hours of that day's clock.
            // من 0 إلى 13 يوم مضى، خلال آخر 6 ساعات من وقت ذلك اليوم.
            saleTimes.add(now.minus(Duration.ofDays(random.nextInt(14))).minus(Duration.ofHours(1 + random.nextInt(6))));
        }
        saleTimes.sort(null);
        // Shop orders are placed between the office sales in time order, so each product's ledger stays in date order.
        // طلبات المتجر تنحط بين مبيعات المكتب بترتيب الوقت، حتى سجل كل منتج يبقى مرتب حسب التاريخ.
        List<ShopPlan> shopOrders = new ArrayList<>(shopPlans(now));
        for (Instant when : saleTimes) {
            while (!shopOrders.isEmpty() && shopOrders.getFirst().when().isBefore(when)) {
                shopOrders.removeFirst().place().run();
            }
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
            backdate(order.number(), when);
        }
        shopOrders.forEach(plan -> plan.place().run());
        // One draft and one cancelled order to show every status in the UI.
        // مسودة وطلب ملغي حتى تظهر كل الحالات في الواجهة.
        sales.create(new SalesOrderRequest(customerIds.get(1), "Waiting for customer approval",
                List.of(new LineRequest(id(created, "STA-001"), 20, new BigDecimal("17.50")))));
        SalesOrderResponse cancelled = sales.create(new SalesOrderRequest(customerIds.get(2), null,
                List.of(new LineRequest(id(created, "BEV-002"), 6, null))));
        sales.confirm(cancelled.id());
        sales.cancel(cancelled.id(), null);
    }

    // Same idea as backdate() for a received purchase order and its stock movements.
    // نفس فكرة backdate() لطلب شراء مستلم وحركات مخزونه.
    private void backdatePurchase(Long purchaseOrderId, Instant when) {
        productRepository.flush();
        java.sql.Timestamp ts = java.sql.Timestamp.from(when);
        String number = jdbc.queryForObject("select number from purchase_order where id = ?", String.class, purchaseOrderId);
        jdbc.update("update purchase_order set created_at = ?, received_at = ? where id = ?", ts, ts, purchaseOrderId);
        jdbc.update("update stock_movement set created_at = ? where reference = ?", ts, number);
    }

    private ProductResponse product(String sku, String name, long categoryId, long supplierId, String unit,
                                    String cost, String price, int reorder, int opening) {
        String[] shop = SHOP_TEXT.get(sku);
        // Products without shop text stay office-only, to show that publishing is a choice.
        // المنتجات اللي ما لها نص للمتجر تبقى للمكتب بس، عشان يبان إن النشر اختيار.
        ShopListing listing = shop == null ? null : new ShopListing(true, shop[0], shop[1], shop[2]);
        return productService.create(new CreateProductRequest(sku, name, categoryId, supplierId, unit,
                new BigDecimal(cost), new BigDecimal(price), reorder, opening, listing));
    }

    // Arabic name, English description, Arabic description of each product shown in the shop.
    // الاسم بالعربي، والوصف بالإنجليزي، والوصف بالعربي لكل منتج يظهر في المتجر.
    private static final Map<String, String[]> SHOP_TEXT = Map.ofEntries(
            Map.entry("BEV-001", new String[]{"مياه معدنية 330 مل (40 حبة)",
                    "Natural mineral water in handy 330 ml bottles, 40 per carton.",
                    "مياه معدنية طبيعية بعبوات 330 مل سهلة الحمل، 40 حبة بالكرتون."}),
            Map.entry("BEV-002", new String[]{"عصير برتقال 1 لتر",
                    "100% orange juice with no added sugar. Keep refrigerated after opening.",
                    "عصير برتقال 100% بدون سكر مضاف. يحفظ بالثلاجة بعد الفتح."}),
            Map.entry("BEV-003", new String[]{"قهوة عربية 500 جم",
                    "Lightly roasted Arabic coffee with cardamom, ground and ready for the dallah.",
                    "قهوة عربية محمصة تحميص خفيف مع الهيل، مطحونة وجاهزة للدلة."}),
            Map.entry("BEV-004", new String[]{"شاي أخضر (100 كيس)",
                    "Green tea in 100 individually wrapped bags.",
                    "شاي أخضر 100 كيس مغلّف كل واحد لحاله."}),
            Map.entry("GRO-001", new String[]{"أرز بسمتي 5 كجم",
                    "Long-grain aged basmati rice that cooks fluffy, ideal for kabsa and mandi.",
                    "أرز بسمتي طويل الحبة ومعتّق، يطلع مفلفل ومناسب للكبسة والمندي."}),
            Map.entry("GRO-002", new String[]{"زيت دوار الشمس 1.8 لتر",
                    "Pure sunflower oil for frying and cooking.",
                    "زيت دوار الشمس نقي للقلي والطبخ."}),
            Map.entry("GRO-003", new String[]{"سكر أبيض 2 كجم",
                    "Fine white sugar in a resealable bag.",
                    "سكر أبيض ناعم بكيس يتسكّر."}),
            Map.entry("GRO-004", new String[]{"تمر سكري 1 كجم",
                    "Qassim Sukkari dates, soft and sweet, packed in a gift box.",
                    "تمر سكري القصيم، طري وحالي، معبّأ بعلبة هدية."}),
            Map.entry("CLN-001", new String[]{"سائل غسيل صحون 1 لتر",
                    "Lemon dishwashing liquid that cuts grease and is gentle on hands.",
                    "سائل غسيل صحون بالليمون يشيل الدهون ولطيف على اليدين."}),
            Map.entry("CLN-002", new String[]{"مسحوق غسيل 3 كجم",
                    "Laundry powder for front and top loading machines.",
                    "مسحوق غسيل للغسالات الأمامية والعلوية."}),
            Map.entry("CLN-003", new String[]{"منظف أرضيات 2 لتر",
                    "Floor cleaner with a long-lasting fresh scent.",
                    "منظف أرضيات بريحة منعشة تدوم."}),
            Map.entry("STA-001", new String[]{"ورق تصوير A4 (500 ورقة)",
                    "80 gsm A4 copy paper for printers and copiers.",
                    "ورق تصوير A4 وزن 80 جم للطابعات وآلات التصوير."}),
            Map.entry("ELC-001", new String[]{"شاحن USB-C بقوة 20 واط",
                    "20 W fast charger with a USB-C port, works with phones and tablets.",
                    "شاحن سريع 20 واط بمنفذ USB-C، يشتغل مع الجوالات والأجهزة اللوحية."}),
            Map.entry("ELC-002", new String[]{"ماوس لاسلكي",
                    "Quiet wireless mouse with a USB receiver and up to 12 months battery life.",
                    "ماوس لاسلكي هادي مع مستقبل USB، وبطاريته تكفي لين 12 شهر."}),
            Map.entry("ELC-003", new String[]{"كيبل HDMI طول 2 متر",
                    "High-speed HDMI cable, 2 m, supports 4K.",
                    "كيبل HDMI عالي السرعة، طول 2 متر، يدعم 4K."}));

    /**
     * Shop customers and online orders in every state, created through the real checkout so stock, totals and
     * payments follow the same rules as a real order.
     * عملاء المتجر وطلبات أونلاين بكل الحالات، تنشأ من عملية الشراء الحقيقية حتى المخزون والإجماليات والدفع يمشون
     * على نفس قواعد الطلب الحقيقي.
     */
    private List<ShopPlan> shopPlans(Instant now) {
        String password = passwordEncoder.encode(properties.seed().customerPassword());
        AppUser nora = customer("966553001010", "Nora Al-Qahtani", "nora@example.com", password);
        AppUser faisal = customer("966553001001", "Faisal Al-Harbi", null, password);

        // who (null = guest), what, how they pay, what happened next, and when it was ordered.
        // مين (null = زائر)، وش طلب، وكيف يدفع، وش صار بعدها، ومتى طلب.
        List<ShopPlan> plans = new ArrayList<>(List.of(
                plan(now.minus(Duration.ofDays(6)), nora, null, null,
                        List.of(item("GRO-001", 2), item("BEV-003", 1)), Payment.Method.CARD, "DELIVERED"),
                plan(now.minus(Duration.ofDays(3)), null, "Sara Al-Otaibi", "0561234567",
                        List.of(item("CLN-001", 3), item("CLN-003", 2)), Payment.Method.CASH, "DELIVERED"),
                plan(now.minus(Duration.ofDays(2)), faisal, null, null,
                        List.of(item("GRO-003", 2)), Payment.Method.CARD, "CANCELLED"),
                plan(now.minus(Duration.ofDays(1)), nora, null, null,
                        List.of(item("ELC-001", 1), item("ELC-003", 2), item("GRO-004", 1)), Payment.Method.TABBY,
                        "SHIPPED"),
                plan(now.minus(Duration.ofMinutes(400)), null, "Khalid Al-Dosari", "0509876543",
                        List.of(item("BEV-001", 4), item("GRO-002", 2)), Payment.Method.TAMARA, "NEW"),
                plan(now.minus(Duration.ofMinutes(30)), faisal, null, null,
                        List.of(item("STA-001", 2), item("BEV-002", 6)), Payment.Method.CASH, "NEW"),
                plan(now.minus(Duration.ofMinutes(20)), nora, null, null,
                        List.of(item("BEV-004", 2)), Payment.Method.CARD, "NEW")));
        plans.sort(java.util.Comparator.comparing(ShopPlan::when));
        return plans;
    }

    // One demo shop order, placed when its turn comes in the timeline. / طلب متجر تجريبي، ينطلب لما يجي دوره بالترتيب الزمني.
    private record ShopPlan(Instant when, Runnable place) {
    }

    private ShopPlan plan(Instant when, AppUser account, String guestName, String guestPhone, List<CartItem> items,
                          Payment.Method method, String outcome) {
        return new ShopPlan(when, () -> shopOrder(account, guestName, guestPhone, items, method, outcome, when));
    }

    private AppUser customer(String phone, String name, String email, String passwordHash) {
        AppUser user = new AppUser(phone, name, passwordHash, Role.CUSTOMER);
        user.setPhone(phone);
        user.setEmail(email);
        return users.save(user);
    }

    private CartItem item(String sku, int quantity) {
        return new CartItem(productRepository.findBySkuIgnoreCase(sku).orElseThrow().getId(), quantity);
    }

    private void shopOrder(AppUser account, String guestName, String guestPhone, List<CartItem> items,
                           Payment.Method method, String outcome, Instant when) {
        // Online methods are paid on the built-in test checkout. With a real gateway configured that is not
        // possible from here, so those demo orders become cash on delivery instead.
        // الطرق الأونلاين تندفع على صفحة الدفع التجريبية المدمجة. ولو فيه بوابة حقيقية مضبوطة ما ينفع من هنا،
        // فهالطلبات التجريبية تصير دفع عند الاستلام بدالها.
        Payment.Method how = method == Payment.Method.CASH
                || gateways.forMethod(method).provider() == Payment.Provider.SANDBOX ? method : Payment.Method.CASH;
        if (account != null) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    account.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        }
        CheckoutResult result;
        try {
            result = shop.checkout(new CheckoutRequest(items, guestName, guestPhone, null, City.RIYADH,
                    "Al Olaya", "King Fahd Road, building 12", how, null));
        } finally {
            SecurityContextHolder.clearContext();
        }
        if (result.paymentId() != null && !outcome.equals("CANCELLED")) {
            boolean card = how == Payment.Method.CARD;
            paymentService.payInSandbox(result.paymentId(), new SandboxRequest(card ? "4406 4700 0000 0007" : null,
                    card ? 12 : null, card ? YearMonth.now().getYear() + 2 : null, card ? "123" : null,
                    card ? "Demo Customer" : null, card ? null : Boolean.TRUE));
        }
        Long id = jdbc.queryForObject("select id from sales_order where number = ?", Long.class, result.number());
        switch (outcome) {
            case "SHIPPED" -> sales.ship(id);
            case "DELIVERED" -> {
                sales.ship(id);
                sales.deliver(id, true);
            }
            case "CANCELLED" -> sales.cancel(id, null);
            default -> {
            }
        }
        backdate(result.number(), when);
        java.sql.Timestamp paid = java.sql.Timestamp.from(when.plus(Duration.ofMinutes(3)));
        // The later steps happened hours after the order, like in a real shop. / الخطوات اللي بعده صارت بعد ساعات، مثل محل حقيقي.
        java.sql.Timestamp delivered = java.sql.Timestamp.from(when.plus(Duration.ofHours(28)));
        jdbc.update("update payment set created_at = ? where order_id = ? and method <> 'CASH'",
                java.sql.Timestamp.from(when.plus(Duration.ofMinutes(1))), id);
        jdbc.update("update payment set paid_at = ? where order_id = ? and method <> 'CASH' and paid_at is not null",
                paid, id);
        jdbc.update("update payment set created_at = ?, paid_at = ? where order_id = ? and method = 'CASH'",
                delivered, delivered, id);
        jdbc.update("update sales_order set pay_by = null where id = ?", id);
        jdbc.update("update sales_order set shipped_at = ? where id = ? and shipped_at is not null",
                java.sql.Timestamp.from(when.plus(Duration.ofHours(4))), id);
        jdbc.update("update sales_order set delivered_at = ? where id = ? and delivered_at is not null", delivered, id);
        jdbc.update("update sales_order set cancelled_at = ? where id = ? and cancelled_at is not null",
                java.sql.Timestamp.from(when.plus(Duration.ofMinutes(20))), id);
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
