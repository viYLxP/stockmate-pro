package com.stockmate.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;

import com.stockmate.common.City;
import com.stockmate.order.OrderTotals;
import com.stockmate.partner.Customer;

/**
 * A sales order (invoice) for a customer.
 * طلب بيع (فاتورة) لعميل.
 */
@Entity
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. "SO-000017", from its own database sequence.
    // مثل "SO-000017"، من sequence خاص في قاعدة البيانات.
    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesStatus status = SalesStatus.DRAFT;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Set when the order is confirmed; sales reports use this date, not the creation date.
    // يتحدد عند تأكيد الطلب، وتقارير المبيعات تعتمد على هذا التاريخ مو تاريخ الإنشاء.
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    // ----- Online shop / المتجر الإلكتروني -----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SalesChannel channel = SalesChannel.OFFICE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    // What the shopper picked at checkout; CASH means cash on delivery. / اللي اختاره المتسوق؛ CASH يعني الدفع عند الاستلام.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_choice", length = 20)
    private com.stockmate.payment.Payment.Method paymentChoice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Fulfilment fulfilment;

    @Column(name = "ship_name", length = 120)
    private String shipName;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @Column(name = "ship_email", length = 150)
    private String shipEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "ship_city", length = 20)
    private City shipCity;

    @Column(name = "ship_district", length = 100)
    private String shipDistrict;

    @Column(name = "ship_address", length = 300)
    private String shipAddress;

    // Stock is held for an online payment until this time. / المخزون محجوز للدفع أونلاين لين هالوقت.
    @Column(name = "pay_by")
    private Instant payBy;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // Optimistic locking, checked when an order is locked for paying or cancelling (see OrderLock).
    // قفل متفائل، ينفحص لما ينقفل الطلب للدفع أو الإلغاء (شوف OrderLock).
    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SalesOrderLine> lines = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(String number, Customer customer, String createdBy) {
        this.number = number;
        this.customer = customer;
        this.createdBy = createdBy;
    }

    /**
     * Replaces all lines and recomputes the totals in one step.
     * يستبدل كل الأسطر ويعيد حساب الإجماليات بخطوة وحدة.
     */
    public void replaceLines(List<SalesOrderLine> newLines, BigDecimal vatRate) {
        lines.clear();
        newLines.forEach(line -> {
            line.attachTo(this);
            lines.add(line);
        });
        OrderTotals totals = OrderTotals.of(lines.stream().map(SalesOrderLine::getLineTotal).toList(), vatRate);
        subtotal = totals.subtotal();
        vat = totals.vat();
        total = totals.total();
    }

    public void markConfirmed() {
        status = SalesStatus.CONFIRMED;
        confirmedAt = Instant.now();
    }

    public void markCancelled() {
        status = SalesStatus.CANCELLED;
        cancelledAt = Instant.now();
        payBy = null;
    }

    /**
     * Turns this order into a shop order with its delivery details.
     * يحوّل الطلب لطلب متجر مع بيانات التوصيل.
     */
    public void makeOnline(String name, String phone, String email, City city, String district, String address,
                           com.stockmate.payment.Payment.Method choice) {
        channel = SalesChannel.ONLINE;
        fulfilment = Fulfilment.NEW;
        shipName = name;
        shipPhone = phone;
        shipEmail = email;
        shipCity = city;
        shipDistrict = district;
        shipAddress = address;
        paymentChoice = choice;
    }

    public void markShipped() {
        fulfilment = Fulfilment.SHIPPED;
        shippedAt = Instant.now();
    }

    public void markDelivered() {
        fulfilment = Fulfilment.DELIVERED;
        deliveredAt = Instant.now();
    }

    public SalesChannel getChannel() {
        return channel;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
        // Paid: the stock no longer needs a deadline. / انْدفع: المخزون ما عاد يحتاج موعد.
        if (paymentStatus == PaymentStatus.PAID) {
            payBy = null;
        }
    }

    public com.stockmate.payment.Payment.Method getPaymentChoice() {
        return paymentChoice;
    }

    public Fulfilment getFulfilment() {
        return fulfilment;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipPhone() {
        return shipPhone;
    }

    public String getShipEmail() {
        return shipEmail;
    }

    public City getShipCity() {
        return shipCity;
    }

    public String getShipDistrict() {
        return shipDistrict;
    }

    public String getShipAddress() {
        return shipAddress;
    }

    public Instant getPayBy() {
        return payBy;
    }

    public void setPayBy(Instant payBy) {
        this.payBy = payBy;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public SalesStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getVat() {
        return vat;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public List<SalesOrderLine> getLines() {
        return lines;
    }
}
