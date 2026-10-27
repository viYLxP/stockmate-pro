package com.stockmate.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stockmate.sales.SalesOrder;

/**
 * Database access for payments.
 * الوصول لجدول المدفوعات.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderOrderByCreatedAtAsc(SalesOrder order);

    Optional<Payment> findFirstByOrderAndStatus(SalesOrder order, Payment.Status status);

    List<Payment> findByOrderAndStatus(SalesOrder order, Payment.Status status);

    // The payment page and the gateways need the order and its lines in one go. / صفحة الدفع والبوابات تحتاج الطلب وبنوده مرة وحدة.
    @EntityGraph(attributePaths = {"order", "order.lines", "order.lines.product"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithOrder(@Param("id") UUID id);

    // Money actually received in a period (refunds excluded), for the dashboard.
    // الفلوس اللي وصلت فعلًا في فترة (بدون المسترجع)، للوحة التحكم.
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.stockmate.payment.Payment.Status.PAID and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal collected(@Param("from") Instant from, @Param("to") Instant to);
}
