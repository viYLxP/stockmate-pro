package com.stockmate.payment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.stockmate.common.ApiException;
import com.stockmate.common.ErrorCode;
import com.stockmate.sales.SalesOrder;

/**
 * Tamara instalments: the customer splits the amount into 4 payments; we receive it in full.
 * تقسيط تمارا: العميل يقسم المبلغ على 4 دفعات؛ واحنا نستلمه كامل.
 *
 * <p>Flow (Tamara API): create a checkout and send the customer to checkout_url. When they finish, the order is
 * "approved"; we must then authorise it, and from "authorised" the order counts as paid (Tamara captures it
 * automatically after 21 days if we do not).</p>
 * <p>المسار (Tamara API): ننشئ checkout وننقل العميل لـ checkout_url. لما يخلّص يصير الطلب "approved"؛ ولازم
 * بعدها نسوي له authorise، ومن "authorised" ينحسب الطلب مدفوع (تمارا تسوي capture تلقائي بعد 21 يوم لو ما سويناه).</p>
 */
public class TamaraGateway implements PaymentGateway {

    private final RestClient client;
    private final int instalments;

    public TamaraGateway(RestClient.Builder builder, String apiUrl, String apiToken, int instalments) {
        this.client = builder.baseUrl(apiUrl)
                .defaultHeaders(h -> h.setBearerAuth(apiToken))
                .build();
        this.instalments = instalments;
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.TAMARA;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        SalesOrder r = payment.getOrder();
        String currency = payment.getCurrency();
        BigDecimal vat = r.getVat();
        String[] names = splitName(r.getShipName());
        Map<String, Object> consumer = new LinkedHashMap<>();
        consumer.put("first_name", names[0]);
        consumer.put("last_name", names[1]);
        consumer.put("phone_number", "+" + r.getShipPhone());
        if (r.getShipEmail() != null) {
            consumer.put("email", r.getShipEmail());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        // Our payment id is unique per attempt, so a retry never collides with an older Tamara order.
        // رقم دفعتنا فريد لكل محاولة، فإعادة المحاولة ما تتعارض مع طلب تمارا قديم.
        body.put("order_reference_id", payment.getId().toString());
        body.put("order_number", r.getNumber());
        body.put("total_amount", money(payment.getAmount(), currency));
        body.put("shipping_amount", money(BigDecimal.ZERO, currency));
        body.put("tax_amount", money(vat, currency));
        body.put("description", "StockMate " + r.getNumber());
        body.put("country_code", "SA");
        body.put("payment_type", "PAY_BY_INSTALMENTS");
        body.put("instalments", instalments);
        body.put("locale", "ar_SA");
        body.put("items", r.getLines().stream().map(l -> Map.<String, Object>of("name", l.getProduct().getName(),
                "type", "Physical", "reference_id", l.getProduct().getSku(), "sku", l.getProduct().getSku(),
                "quantity", l.getQuantity(), "unit_price", money(l.getUnitPrice(), currency),
                "total_amount", money(l.getLineTotal(), currency))).toList());
        body.put("consumer", consumer);
        body.put("shipping_address", Map.of("first_name", names[0], "last_name", names[1],
                "line1", r.getShipDistrict() + ", " + r.getShipAddress(),
                "city", r.getShipCity().name(), "country_code", "SA", "phone_number", "+" + r.getShipPhone()));
        body.put("merchant_url", Map.of("success", urls.successUrl(), "failure", urls.failureUrl(),
                "cancel", urls.backUrl(), "notification", urls.successUrl()));
        body.put("risk_assessment", Map.of());
        Order order = GatewaySupport.call("Tamara", () -> client.post().uri("/checkout")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Order.class));
        if (order == null || order.order_id() == null || order.checkout_url() == null) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE);
        }
        return new Checkout(order.order_id(), order.checkout_url());
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        Order order = GatewaySupport.call("Tamara", () -> client.get()
                .uri("/merchants/orders/{id}", payment.getProviderRef()).retrieve().body(Order.class));
        if (order == null) {
            return Optional.empty();
        }
        if (order.total_amount() != null && !GatewaySupport.same(order.total_amount().amount(), payment.getAmount())) {
            return Optional.of(new Result(Payment.Status.FAILED, "tamara", null, "AMOUNT_MISMATCH"));
        }
        return switch (order.status() == null ? "" : order.status()) {
            case "approved" -> {
                Order authorised = GatewaySupport.call("Tamara", () -> client.post()
                        .uri("/orders/{id}/authorise", payment.getProviderRef()).retrieve().body(Order.class));
                yield authorised != null && "authorised".equals(authorised.status())
                        ? Optional.of(new Result(Payment.Status.PAID, "tamara", null, null))
                        : Optional.empty();
            }
            case "authorised", "partially_captured", "fully_captured" ->
                    Optional.of(new Result(Payment.Status.PAID, "tamara", null, null));
            case "declined", "expired", "canceled" ->
                    Optional.of(new Result(Payment.Status.FAILED, "tamara", null, order.status().toUpperCase()));
            default -> Optional.empty();
        };
    }

    @Override
    public void refund(Payment payment) {
        GatewaySupport.call("Tamara", () -> client.post()
                .uri("/payments/simplified-refund/{id}", payment.getProviderRef())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("total_amount", money(payment.getAmount(), payment.getCurrency()),
                        "comment", "Order " + payment.getOrder().getNumber() + " cancelled"))
                .retrieve().toBodilessEntity());
    }

    private static Map<String, Object> money(BigDecimal amount, String currency) {
        return Map.of("amount", amount, "currency", currency);
    }

    // Tamara wants first and last name apart; a one-word name repeats it. / تمارا تبي الاسم الأول والأخير منفصلين.
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[]{trimmed, trimmed}
                : new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Order(String order_id, String checkout_url, String status, Amount total_amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Amount(BigDecimal amount, String currency) {
    }
}
