package com.stockmate.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockmate.common.ApiException;
import com.stockmate.common.CurrentUser;
import com.stockmate.common.ErrorCode;
import com.stockmate.config.AppProperties;
import com.stockmate.payment.PaymentDtos.CheckoutPage;
import com.stockmate.payment.PaymentDtos.CheckoutResponse;
import com.stockmate.payment.PaymentDtos.Instalment;
import com.stockmate.payment.PaymentDtos.PaymentSummary;
import com.stockmate.payment.PaymentDtos.PaymentView;
import com.stockmate.payment.PaymentDtos.SandboxRequest;
import com.stockmate.sales.Fulfilment;
import com.stockmate.sales.OrderLock;
import com.stockmate.sales.PaymentStatus;
import com.stockmate.sales.SalesChannel;
import com.stockmate.sales.SalesOrder;
import com.stockmate.sales.SalesStatus;

/**
 * Payment rules for shop orders: when an order can be paid, online checkout (card, Apple Pay, Tabby, Tamara),
 * cash on delivery, and refunds on cancellation.
 * قواعد الدفع لطلبات المتجر: متى ينفع يندفع الطلب، والدفع أونلاين (بطاقة، Apple Pay، تابي، تمارا)، والدفع عند
 * الاستلام، والاسترجاع عند الإلغاء.
 *
 * <p>The amount is always the order total read from the database, never a number sent by the browser.</p>
 * <p>المبلغ دايم هو إجمالي الطلب من قاعدة البيانات، أبدًا رقم جاي من المتصفح.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Gateways refuse tiny charges (Moyasar's minimum is 1 SAR). / البوابات ترفض المبالغ الصغيرة جدًا (أقل شي عند Moyasar ريال).
    private static final BigDecimal MIN_ONLINE = BigDecimal.ONE;

    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final ZoneId zone;
    private final String publicUrl;
    private final AppProperties.Instalments instalments;
    private final OrderLock locks;

    public PaymentService(PaymentRepository payments, PaymentGateways gateways, AppProperties properties,
                          OrderLock locks) {
        this.locks = locks;
        this.payments = payments;
        this.gateways = gateways;
        this.zone = properties.timeZone();
        this.instalments = properties.payments().instalments();
        // Without the trailing slash, so links never get "//". / بدون الشرطة الأخيرة، حتى الروابط ما يصير فيها "//".
        this.publicUrl = properties.payments().publicUrl().replaceAll("/+$", "");
    }

    /**
     * Opens a checkout with the chosen method. Reuses a still-open attempt of the same method, so pressing "Pay"
     * twice does not create two invoices the customer could both pay.
     * يفتح الدفع بالطريقة المختارة. يعيد استخدام المحاولة المفتوحة لنفس الطريقة، حتى الضغط على "ادفع" مرتين ما
     * يسوي فاتورتين ممكن العميل يدفعهم الثنتين.
     */
    @Transactional
    public CheckoutResponse startOnline(SalesOrder order, Payment.Method method) {
        requireOnlinePayable(order);
        if (!PaymentGateways.ONLINE.contains(method)) {
            throw fieldError("method", "Online");
        }
        BigDecimal amount = order.getTotal();
        if (method.isInstalments() && !instalmentsAllowed(amount)) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE,
                    Map.of("min", instalments.minAmount(), "max", instalments.maxAmount()));
        }
        PaymentGateway gateway = gateways.forMethod(method);
        for (Payment open : payments.findByOrderAndStatus(order, Payment.Status.PENDING)) {
            refresh(open);
            if (order.getPaymentStatus() != PaymentStatus.UNPAID) {
                // It turned out the customer had already paid with this attempt. / طلع إن العميل دفع بهالمحاولة.
                throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
            }
            if (open.getStatus() != Payment.Status.PENDING || open.getMethod() != method) {
                continue;
            }
            if (open.getProvider() == gateway.provider() && open.getAmount().compareTo(amount) == 0) {
                return new CheckoutResponse(open.getId(), open.getCheckoutUrl());
            }
            // The price or the gateway changed since: close the old attempt. / السعر أو البوابة تغيّرت: نقفل المحاولة القديمة.
            open.markFailed("SUPERSEDED", null, null);
        }
        // Online payments are made by the customer, signed in or not. / الدفع أونلاين يسويه العميل، مسجّل دخول أو لا.
        Payment payment = new Payment(order, method, gateway.provider(), amount, "customer", Instant.now());
        PaymentGateway.Checkout checkout = gateway.start(payment, urls(payment));
        payment.setProviderRef(checkout.providerRef());
        payment.setCheckoutUrl(checkout.url());
        payments.save(payment);
        return new CheckoutResponse(payment.getId(), checkout.url());
    }

    /**
     * The payment page. For a real gateway it first asks the gateway for the latest status, so a customer coming
     * back from Moyasar sees "paid" at once.
     * صفحة الدفع. للبوابة الحقيقية تسأل البوابة أول عن آخر حالة، حتى العميل الراجع من Moyasar يشوف "مدفوع" على طول.
     */
    @Transactional
    public CheckoutPage page(UUID id) {
        Payment payment = find(id);
        if (payment.getStatus() == Payment.Status.PENDING) {
            locks.lock(payment.getOrder());
        }
        refresh(payment);
        return page(payment);
    }

    /**
     * The customer's answer on the sandbox page: a test card, or approving / cancelling Apple Pay, Tabby or Tamara.
     * A declined card leaves the attempt open for another card.
     * رد العميل في صفحة الدفع التجريبية: بطاقة تجريبية، أو الموافقة / الإلغاء في Apple Pay أو تابي أو تمارا.
     * البطاقة المرفوضة تخلي المحاولة مفتوحة لبطاقة ثانية.
     */
    @Transactional
    public CheckoutPage payInSandbox(UUID id, SandboxRequest answer) {
        Payment payment = find(id);
        if (payment.getProvider() != Payment.Provider.SANDBOX) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        if (payment.getStatus() == Payment.Status.PAID) {
            return page(payment);
        }
        if (payment.getStatus() == Payment.Status.REFUNDED || "SUPERSEDED".equals(payment.getFailureReason())) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        locks.lock(payment.getOrder());
        requireOnlinePayable(payment.getOrder());
        if (payment.getMethod() == Payment.Method.CARD) {
            chargeTestCard(payment, answer);
        } else if (answer.approve() == null) {
            throw fieldError("approve", "NotNull");
        } else if (answer.approve()) {
            settle(payment, payment.getMethod().name().toLowerCase(), null);
        } else {
            payment.markFailed("CANCELLED_BY_CUSTOMER", null, null);
        }
        return page(payment);
    }

    private void chargeTestCard(Payment payment, SandboxRequest card) {
        Map<String, String> missing = new java.util.LinkedHashMap<>();
        if (card.cardNumber() == null || card.cardNumber().isBlank()) {
            missing.put("cardNumber", "NotBlank");
        }
        if (card.expMonth() == null) {
            missing.put("expMonth", "NotNull");
        }
        if (card.expYear() == null) {
            missing.put("expYear", "NotNull");
        }
        if (card.cvc() == null || card.cvc().isBlank()) {
            missing.put("cvc", "NotBlank");
        }
        if (card.cardHolder() == null || card.cardHolder().isBlank()) {
            missing.put("cardHolder", "NotBlank");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", missing));
        }
        String digits = SandboxCards.clean(card.cardNumber());
        if (digits == null) {
            throw fieldError("cardNumber", "CardNumber");
        }
        if (!SandboxCards.expiryValid(card.expMonth(), card.expYear(), YearMonth.now(zone))) {
            throw fieldError("expYear", "CardExpired");
        }
        SandboxCards.Outcome outcome = SandboxCards.charge(digits);
        if (outcome.approved()) {
            settle(payment, outcome.brand(), outcome.last4());
        } else {
            payment.markFailed(outcome.failureReason(), outcome.brand(), outcome.last4());
        }
    }

    /**
     * Records cash collected on delivery (or a bank transfer the office confirmed).
     * يسجّل الكاش المستلم عند التوصيل (أو تحويل بنكي تأكد منه المكتب).
     */
    @Transactional
    public void recordCash(SalesOrder order) {
        if (!isOpenOnlineOrder(order)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        Payment payment = new Payment(order, Payment.Method.CASH, Payment.Provider.CASH, order.getTotal(),
                CurrentUser.username(), Instant.now());
        payment.markPaid(Instant.now(), null, null);
        order.setPaymentStatus(PaymentStatus.PAID);
        // An open card attempt can no longer be used. / محاولة البطاقة المفتوحة ما عاد تنفع.
        payments.findByOrderAndStatus(order, Payment.Status.PENDING)
                .forEach(p -> p.markFailed("SUPERSEDED", null, null));
        payments.save(payment);
    }

    /**
     * Asks the gateways about the order's open attempts, so a payment made at the last second is not missed.
     * The caller locks the order first.
     * يسأل البوابات عن المحاولات المفتوحة للطلب، حتى ما يفوتنا دفع صار بآخر ثانية. واللي يناديها يقفل الطلب أول.
     */
    @Transactional
    public void refreshPending(SalesOrder order) {
        payments.findByOrderAndStatus(order, Payment.Status.PENDING).forEach(this::refresh);
    }

    /**
     * Called when an order is cancelled: returns the money of a paid order and closes open attempts.
     * ينادى لما ينلغي طلب: يرجّع فلوس الطلب المدفوع ويقفل المحاولات المفتوحة.
     */
    @Transactional
    public void refundIfPaid(SalesOrder order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            payments.findByOrderAndStatus(order, Payment.Status.PENDING)
                    .forEach(p -> p.markFailed("SUPERSEDED", null, null));
            return;
        }
        Payment paid = payments.findFirstByOrderAndStatus(order, Payment.Status.PAID)
                .orElseThrow(() -> new IllegalStateException("PAID order without a paid payment"));
        if (paid.getMethod() != Payment.Method.CASH) {
            // The gateway is called first: if it fails, the cancellation is rolled back with it.
            // نتصل بالبوابة أول: لو فشلت، الإلغاء يتراجع معها.
            gateways.of(paid.getProvider()).refund(paid);
        }
        paid.markRefunded(Instant.now());
        order.setPaymentStatus(PaymentStatus.REFUNDED);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> history(SalesOrder order) {
        return payments.findByOrderOrderByCreatedAtAsc(order).stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(SalesOrder order) {
        Payment settled = order.getPaymentStatus() == PaymentStatus.UNPAID ? null
                : payments.findByOrderOrderByCreatedAtAsc(order).stream()
                .filter(p -> p.getStatus() == Payment.Status.PAID || p.getStatus() == Payment.Status.REFUNDED)
                .reduce((a, b) -> b).orElse(null);
        BigDecimal due = order.getTotal();
        boolean payable = isOnlinePayable(order);
        List<Payment.Method> methods = !payable ? List.of() : PaymentGateways.ONLINE.stream()
                .filter(m -> !m.isInstalments() || instalmentsAllowed(due)).toList();
        return new PaymentSummary(order.getPaymentStatus(), payable, due, methods,
                payable && instalmentsAllowed(due) ? plan(due) : List.of(),
                settled == null ? null : settled.getMethod(),
                settled == null ? null : settled.getCardBrand(),
                settled == null ? null : settled.getCardLast4(),
                settled == null ? null : settled.getPaidAt());
    }

    /**
     * A confirmed shop order that is not paid or delivered yet (and not too small for a gateway).
     * طلب متجر مؤكد ما انْدفع ولا توصّل (ومو أصغر من الحد اللي تقبله البوابات).
     */
    public boolean isOnlinePayable(SalesOrder order) {
        return isOpenOnlineOrder(order) && order.getTotal().compareTo(MIN_ONLINE) >= 0;
    }

    private static boolean isOpenOnlineOrder(SalesOrder order) {
        return order.getChannel() == SalesChannel.ONLINE && order.getStatus() == SalesStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.UNPAID && order.getFulfilment() != Fulfilment.DELIVERED;
    }

    private boolean instalmentsAllowed(BigDecimal amount) {
        return amount != null && amount.compareTo(instalments.minAmount()) >= 0
                && amount.compareTo(instalments.maxAmount()) <= 0;
    }

    /**
     * Equal monthly instalments, the first one today. Any halala left over from the division goes on the first,
     * so the instalments always add up to the exact total.
     * دفعات شهرية متساوية، أولها اليوم. الهللة الزايدة من القسمة تنضاف على الأولى، فمجموع الدفعات يساوي الإجمالي بالضبط.
     */
    List<Instalment> plan(BigDecimal total) {
        int count = instalments.count();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal first = total.subtract(each.multiply(BigDecimal.valueOf(count - 1)));
        LocalDate today = LocalDate.now(zone);
        List<Instalment> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Instalment(i + 1, i == 0 ? first : each, today.plusMonths(i)));
        }
        return list;
    }

    private void requireOnlinePayable(SalesOrder order) {
        if (!isOnlinePayable(order)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }

    // Asks a real gateway about an open attempt and applies the answer. / يسأل البوابة الحقيقية عن محاولة مفتوحة ويطبّق الرد.
    private void refresh(Payment payment) {
        if (payment.getStatus() != Payment.Status.PENDING || payment.getProvider() == Payment.Provider.SANDBOX) {
            return;
        }
        gateways.of(payment.getProvider()).fetch(payment).ifPresent(result -> {
            if (result.status() == Payment.Status.PAID) {
                settle(payment, result.brand(), result.last4());
            } else if (result.status() == Payment.Status.FAILED) {
                payment.markFailed(result.failureReason(), result.brand(), result.last4());
            }
        });
    }

    /**
     * Marks the payment and the order as paid. If the order was already paid another way (cash, or a second
     * checkout) or was cancelled, this money is returned at once instead of charging the customer for nothing.
     * يعلّم الدفعة والطلب كمدفوعين. لو الطلب انْدفع قبل بطريقة ثانية (كاش، أو دفع ثاني) أو انلغى، نرجّع هالمبلغ
     * فورًا بدل ما ينخصم العميل على الفاضي.
     */
    private void settle(Payment payment, String brand, String last4) {
        SalesOrder order = payment.getOrder();
        // Also when the order was cancelled meanwhile (e.g. the payment window ran out): nothing to pay for.
        // وكمان لو الطلب انلغى بالوقت هذا (مثلًا خلصت مهلة الدفع): ما فيه شي ينْدفع عليه.
        if (order.getPaymentStatus() != PaymentStatus.UNPAID || order.getStatus() == SalesStatus.CANCELLED) {
            log.warn("Duplicate or late payment {} for order {}: refunding", payment.getId(), order.getNumber());
            gateways.of(payment.getProvider()).refund(payment);
            payment.markFailed("DUPLICATE_REFUNDED", brand, last4);
            return;
        }
        payment.markPaid(Instant.now(), brand, last4);
        order.setPaymentStatus(PaymentStatus.PAID);
    }

    private CheckoutPage page(Payment p) {
        SalesOrder o = p.getOrder();
        return new CheckoutPage(p.getId(), p.getMethod(), p.getStatus(), p.getProvider(), p.getAmount(),
                p.getCurrency(), o.getNumber(), o.getLines().size(),
                p.getMethod().isInstalments() ? plan(p.getAmount()) : List.of(), p.getFailureReason(),
                p.getCardBrand(), p.getCardLast4(), p.getPaidAt(), p.getCheckoutUrl());
    }

    private PaymentGateway.Urls urls(Payment payment) {
        String page = publicUrl + "/pay/" + payment.getId();
        return new PaymentGateway.Urls(page, page + "?returned=1", page);
    }

    private Payment find(UUID id) {
        return payments.findWithOrder(id).orElseThrow(() -> ApiException.notFound("payment", id));
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
