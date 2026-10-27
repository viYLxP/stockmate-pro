package com.stockmate.shop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.stockmate.catalog.CategoryRepository;
import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.common.Money;
import com.stockmate.common.PageResponse;
import com.stockmate.common.Phones;
import com.stockmate.common.Text;
import com.stockmate.config.AppProperties;
import com.stockmate.order.OrderTotals;
import com.stockmate.partner.Customer;
import com.stockmate.partner.CustomerRepository;
import com.stockmate.payment.Payment;
import com.stockmate.payment.PaymentDtos.CheckoutResponse;
import com.stockmate.payment.PaymentService;
import com.stockmate.product.Product;
import com.stockmate.product.ProductRepository;
import com.stockmate.sales.Fulfilment;
import com.stockmate.sales.OrderLock;
import com.stockmate.sales.PaymentStatus;
import com.stockmate.sales.SalesChannel;
import com.stockmate.sales.SalesOrder;
import com.stockmate.sales.SalesOrderLine;
import com.stockmate.sales.SalesOrderRepository;
import com.stockmate.sales.SalesService;
import com.stockmate.sales.SalesStatus;
import com.stockmate.shop.ShopDtos.CartItem;
import com.stockmate.shop.ShopDtos.CheckoutRequest;
import com.stockmate.shop.ShopDtos.CheckoutResult;
import com.stockmate.shop.ShopDtos.OrderRow;
import com.stockmate.shop.ShopDtos.OrderView;
import com.stockmate.shop.ShopDtos.Quote;
import com.stockmate.shop.ShopDtos.QuoteLine;
import com.stockmate.shop.ShopDtos.ShopCategory;
import com.stockmate.shop.ShopDtos.ShopProduct;
import com.stockmate.user.AppUser;
import com.stockmate.user.AppUserRepository;
import com.stockmate.user.Role;

/**
 * The online shop: catalogue, cart pricing, checkout, order tracking and the unpaid-order timeout.
 * المتجر الإلكتروني: الكتالوج، وتسعير السلة، وإتمام الطلب، وتتبع الطلب، وانتهاء مهلة الدفع.
 *
 * <p>A shop order is a normal sales order with channel ONLINE. It is confirmed at checkout, so the stock is taken
 * at once and two shoppers can never buy the same last item. An order paid online that is not paid in time is
 * cancelled and its stock returns.</p>
 * <p>طلب المتجر طلب بيع عادي قناته ONLINE. ينأكد وقت إتمام الطلب، فالمخزون ينخصم على طول وما يقدر متسوقين
 * يشترون نفس آخر قطعة. والطلب المدفوع أونلاين اللي ما انْدفع بالوقت ينلغي ويرجع مخزونه.</p>
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CustomerRepository customers;
    private final SalesOrderRepository orders;
    private final SalesService sales;
    private final PaymentService payments;
    private final AppUserRepository users;
    private final OrderLock locks;
    private final TransactionTemplate tx;
    private final AppProperties properties;

    public ShopService(ProductRepository products, CategoryRepository categories, CustomerRepository customers,
                       SalesOrderRepository orders, SalesService sales, PaymentService payments,
                       AppUserRepository users, OrderLock locks, TransactionTemplate tx, AppProperties properties) {
        this.products = products;
        this.categories = categories;
        this.customers = customers;
        this.orders = orders;
        this.sales = sales;
        this.payments = payments;
        this.users = users;
        this.locks = locks;
        this.tx = tx;
        this.properties = properties;
    }

    // ----- Catalogue / الكتالوج -----

    @Transactional(readOnly = true)
    public PageResponse<ShopProduct> products(Long categoryId, String q, Pageable pageable) {
        String pattern = Text.likePattern(q);
        return PageResponse.of(products.shopSearch(categoryId, pattern == null ? "" : pattern, pageable),
                p -> ShopProduct.of(p, properties.vatRate()));
    }

    @Transactional(readOnly = true)
    public ShopProduct product(Long id) {
        return ShopProduct.of(products.findInShop(id).orElseThrow(() -> ApiException.notFound("product", id)),
                properties.vatRate());
    }

    // Only categories that have something to sell. / التصنيفات اللي فيها شي ينباع بس.
    @Transactional(readOnly = true)
    public List<ShopCategory> categories() {
        Map<Long, Long> counts = products.shopCategoryCounts().stream()
                .collect(Collectors.toMap(ProductRepository.CategoryCount::getCategoryId,
                        ProductRepository.CategoryCount::getProductCount));
        return categories.findAllById(counts.keySet()).stream()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                .map(c -> new ShopCategory(c.getId(), c.getName(), c.getNameAr(), counts.get(c.getId())))
                .toList();
    }

    /**
     * Prices the cart with today's prices and stock. The browser keeps the cart, but only this answer counts.
     * يسعّر السلة بأسعار ومخزون اليوم. المتصفح يحفظ السلة، بس هالرد هو المعتمد.
     */
    @Transactional(readOnly = true)
    public Quote quote(List<CartItem> items) {
        requireUnique(items);
        Map<Long, Product> found = products.findAllById(items.stream().map(CartItem::productId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<QuoteLine> lines = new ArrayList<>();
        List<BigDecimal> totals = new ArrayList<>();
        boolean ok = true;
        for (CartItem item : items) {
            Product p = found.get(item.productId());
            if (p == null || !p.isActive() || !p.isPublished()) {
                lines.add(new QuoteLine(item.productId(), p == null ? null : p.getName(),
                        p == null ? null : p.getNameAr(), item.quantity(), null, null, "UNAVAILABLE", 0));
                ok = false;
                continue;
            }
            BigDecimal lineTotal = Money.times(p.getSalePrice(), item.quantity());
            boolean short_ = p.getQuantity() < item.quantity();
            ok &= !short_;
            totals.add(lineTotal);
            lines.add(new QuoteLine(p.getId(), p.getName(), p.getNameAr(), item.quantity(), p.getSalePrice(),
                    lineTotal, short_ ? "STOCK" : null, short_ ? p.getQuantity() : null));
        }
        OrderTotals sum = OrderTotals.of(totals, properties.vatRate());
        return new Quote(lines, sum.subtotal(), sum.vat(), sum.total(), ok);
    }

    // ----- Checkout / إتمام الطلب -----

    /**
     * Creates the order, takes the stock, and opens the payment. All in one transaction: if the gateway refuses,
     * nothing is kept and the stock is not touched.
     * ينشئ الطلب، وياخذ المخزون، ويفتح الدفع. كلها بمعاملة وحدة: لو البوابة رفضت، ما ينحفظ شي والمخزون ما يتغير.
     */
    @Transactional
    public CheckoutResult checkout(CheckoutRequest request) {
        // A filled honeypot means a bot: answer like a success so it does not learn, but save nothing.
        // المصيدة المعبّاة تعني بوت: نرد كأنه نجح حتى ما يتعلم، بس ما نحفظ شي.
        if (Text.blankToNull(request.website()) != null) {
            log.info("Shop checkout dropped: honeypot filled");
            return new CheckoutResult(sales.nextNumber(), BigDecimal.ZERO, request.paymentMethod(), null, null, null);
        }
        requireUnique(request.items());
        AppUser account = signedInCustomer().orElse(null);
        String name;
        String phone;
        String email = Text.blankToNull(request.email());
        if (account != null) {
            name = account.getFullName();
            phone = account.getPhone();
            email = email != null ? email : account.getEmail();
        } else {
            name = Text.blankToNull(request.fullName());
            if (name == null) {
                throw fieldError("fullName", "NotBlank");
            }
            phone = Phones.require(request.phone(), "phone");
        }
        Customer customer = customerFor(account, name, phone, email, request);

        SalesOrder order = new SalesOrder(sales.nextNumber(), customer, account == null ? "shop" : account.getUsername());
        order.replaceLines(lines(request.items()), properties.vatRate());
        order.makeOnline(name, phone, email, request.city(), request.district().trim(), request.address().trim(),
                request.paymentMethod());
        orders.save(order);
        // Stock is taken now, under row locks, so the last item cannot be sold twice. / المخزون ينخصم الحين، بقفل الصفوف.
        sales.confirmOrder(order);
        if (request.paymentMethod() == Payment.Method.CASH) {
            return new CheckoutResult(order.getNumber(), order.getTotal(), Payment.Method.CASH, null, null, null);
        }
        order.setPayBy(Instant.now().plus(properties.shop().unpaidHold()));
        orders.flush();
        CheckoutResponse checkout = payments.startOnline(order, request.paymentMethod());
        return new CheckoutResult(order.getNumber(), order.getTotal(), request.paymentMethod(), checkout.paymentId(),
                checkout.checkoutUrl(), order.getPayBy());
    }

    private List<SalesOrderLine> lines(List<CartItem> items) {
        List<SalesOrderLine> lines = new ArrayList<>();
        for (CartItem item : items) {
            Product p = products.findById(item.productId())
                    .filter(Product::isPublished).filter(Product::isActive)
                    .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_UNAVAILABLE,
                            Map.of("productId", item.productId())));
            // Always today's sale price from the database, never a price sent by the browser.
            // دايم سعر البيع الحالي من قاعدة البيانات، أبدًا سعر جاي من المتصفح.
            lines.add(new SalesOrderLine(p, item.quantity(), p.getSalePrice()));
        }
        return lines;
    }

    /**
     * The customer record the order is filed under: the account's own, or one per guest phone number.
     * سجل العميل اللي ينحفظ الطلب تحته: سجل الحساب نفسه، أو سجل واحد لكل جوال زائر.
     */
    private Customer customerFor(AppUser account, String name, String phone, String email, CheckoutRequest r) {
        String shown = Phones.display(phone);
        String city = cityName(r);
        Optional<Customer> existing = account != null ? customers.findByUser(account)
                : customers.findFirstByPhoneAndUserIsNullOrderByIdAsc(shown);
        Customer customer = existing.orElseGet(Customer::new);
        customer.applyContact(name, shown, email != null ? email : customer.getEmail(), city);
        customer.setUser(account);
        return customers.save(customer);
    }

    // The office's customer list shows cities in English ("Riyadh"). / قائمة عملاء المكتب تعرض المدن بالإنجليزي.
    private static String cityName(CheckoutRequest r) {
        String n = r.city().name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    // ----- Tracking (guests) / التتبع (للزوار) -----

    @Transactional
    public OrderView track(String number, String phone) {
        return view(guestOrder(number, phone));
    }

    @Transactional
    public CheckoutResponse payAsGuest(String number, String phone, Payment.Method method) {
        SalesOrder order = guestOrder(number, phone);
        locks.lock(order);
        return payments.startOnline(order, method);
    }

    @Transactional
    public OrderView cancelAsGuest(String number, String phone) {
        SalesOrder order = guestOrder(number, phone);
        return cancelByCustomer(order);
    }

    // ----- The signed-in customer's orders / طلبات العميل المسجّل -----

    @Transactional(readOnly = true)
    public List<OrderRow> ordersOf(AppUser account) {
        return orders.findShopOrdersOfUser(account.getId()).stream()
                .map(o -> new OrderRow(o.getNumber(), o.getStatus(), o.getFulfilment(), o.getCreatedAt(),
                        o.getLines().stream().mapToInt(SalesOrderLine::getQuantity).sum(), o.getTotal(),
                        o.getPaymentChoice(), o.getPaymentStatus(), payments.isOnlinePayable(o)))
                .toList();
    }

    @Transactional
    public OrderView orderOf(AppUser account, String number) {
        return view(accountOrder(account, number));
    }

    @Transactional
    public CheckoutResponse payAsCustomer(AppUser account, String number, Payment.Method method) {
        SalesOrder order = accountOrder(account, number);
        locks.lock(order);
        return payments.startOnline(order, method);
    }

    @Transactional
    public OrderView cancelAsCustomer(AppUser account, String number) {
        return cancelByCustomer(accountOrder(account, number));
    }

    /**
     * The customer can cancel until the order is shipped. A paid order is refunded to the way it was paid.
     * العميل يقدر يلغي لين ينشحن الطلب. والطلب المدفوع يرجع مبلغه بنفس طريقة الدفع.
     */
    private OrderView cancelByCustomer(SalesOrder order) {
        locks.lock(order);
        if (!cancellable(order)) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status",
                    order.getStatus() == SalesStatus.CONFIRMED ? order.getFulfilment().name() : order.getStatus().name()));
        }
        sales.cancelOrder(order, "CUSTOMER");
        return view(order);
    }

    private static boolean cancellable(SalesOrder order) {
        return order.getStatus() == SalesStatus.CONFIRMED && order.getFulfilment() == Fulfilment.NEW;
    }

    private OrderView view(SalesOrder order) {
        return OrderView.of(order, payments.summary(order), cancellable(order));
    }

    // A wrong number and a wrong phone both give NOT_FOUND, so nobody learns which order numbers exist.
    // الرقم الغلط والجوال الغلط الاثنين يرجعون NOT_FOUND، حتى ما أحد يعرف أي أرقام طلبات موجودة.
    private SalesOrder guestOrder(String number, String phone) {
        String cleanNumber = number.trim().toUpperCase();
        String normalized = Phones.normalize(phone);
        return orders.findWithLinesByNumber(cleanNumber)
                .filter(o -> o.getChannel() == SalesChannel.ONLINE)
                .filter(o -> normalized != null && normalized.equals(o.getShipPhone()))
                .orElseThrow(() -> ApiException.notFound("order", cleanNumber));
    }

    private SalesOrder accountOrder(AppUser account, String number) {
        String cleanNumber = number.trim().toUpperCase();
        return orders.findWithLinesByNumber(cleanNumber)
                .filter(o -> o.getChannel() == SalesChannel.ONLINE)
                .filter(o -> o.getCustomer().getUser() != null && o.getCustomer().getUser().getId().equals(account.getId()))
                .orElseThrow(() -> ApiException.notFound("order", cleanNumber));
    }

    // ----- Unpaid-order timeout / انتهاء مهلة الدفع -----

    /**
     * Cancels online orders not paid in time and returns their stock. Each order has its own transaction, so one
     * failure (e.g. a gateway that does not answer) does not stop the others.
     * يلغي الطلبات الأونلاين اللي ما انْدفعت بالوقت ويرجّع مخزونها. كل طلب بمعاملة لحاله، فلو فشل واحد (مثلًا بوابة
     * ما ردّت) الباقي يكمل.
     *
     * @return how many orders were cancelled / كم طلب انلغى
     */
    public int expireUnpaid() {
        int cancelled = 0;
        for (Long id : orders.findExpiredUnpaid(Instant.now())) {
            try {
                Boolean done = tx.execute(status -> expire(id));
                cancelled += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("Could not expire unpaid order {}: {}", id, e.toString());
            }
        }
        return cancelled;
    }

    private boolean expire(Long id) {
        SalesOrder order = orders.findWithLinesById(id).orElse(null);
        if (order == null) {
            return false;
        }
        locks.lock(order);
        // Checked again under the lock, and the gateways are asked once more: the customer may have just paid.
        // نتحقق مرة ثانية تحت القفل، ونسأل البوابات مرة أخيرة: يمكن العميل توه دفع.
        if (order.getStatus() != SalesStatus.CONFIRMED || order.getPaymentStatus() != PaymentStatus.UNPAID
                || order.getPayBy() == null || order.getPayBy().isAfter(Instant.now())) {
            return false;
        }
        payments.refreshPending(order);
        if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
            return false;
        }
        sales.cancelOrder(order, "PAYMENT_TIMEOUT");
        log.info("Order {} cancelled: not paid in time", order.getNumber());
        return true;
    }

    // ----- Helpers / دوال مساعدة -----

    // Only a CUSTOMER token counts; staff using the shop check out as guests. / رمز العميل بس يُحسب؛ والموظف في المتجر يطلب كزائر.
    public Optional<AppUser> signedInCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        boolean customer = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.CUSTOMER.name()));
        return customer ? users.findByUsernameIgnoreCase(auth.getName()).filter(AppUser::isActive) : Optional.empty();
    }

    private static void requireUnique(List<CartItem> items) {
        Set<Long> seen = new HashSet<>();
        for (CartItem item : items) {
            if (!seen.add(item.productId())) {
                throw fieldError("items", "DuplicateProduct");
            }
        }
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
