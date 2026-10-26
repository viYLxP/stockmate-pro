package com.stockmate.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // Reads the next value of the database sequence used for "PO-000001" style numbers.
    // يقرأ القيمة التالية من sequence قاعدة البيانات لأرقام بصيغة "PO-000001".
    @Query(value = "select nextval('purchase_order_number_seq')", nativeQuery = true)
    long nextNumber();

    boolean existsBySupplierId(Long supplierId);

    @Query("select count(l) > 0 from PurchaseOrderLine l where l.product.id = :productId")
    boolean existsLineWithProduct(@Param("productId") Long productId);

    // Loads supplier + lines + products in one query for the order details page.
    // يحمّل المورد والأسطر والمنتجات باستعلام واحد لصفحة تفاصيل الطلب.
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product"})
    Optional<PurchaseOrder> findWithLinesById(Long id);

    @EntityGraph(attributePaths = "supplier")
    @Query("""
            select o from PurchaseOrder o
            where (:status is null or o.status = :status)
              and (:supplierId is null or o.supplier.id = :supplierId)
              and (:q = '' or lower(o.number) like lower(concat('%', :q, '%')))
            """)
    Page<PurchaseOrder> search(@Param("status") PurchaseStatus status, @Param("supplierId") Long supplierId,
                               @Param("q") String q, Pageable pageable);

    // Total value of goods received in a period (for the dashboard).
    // إجمالي قيمة البضاعة المستلمة خلال فترة (للوحة التحكم).
    @Query("""
            select coalesce(sum(o.total), 0) from PurchaseOrder o
            where o.status = com.stockmate.purchase.PurchaseStatus.RECEIVED
              and o.receivedAt >= :from and o.receivedAt < :to
            """)
    BigDecimal receivedTotal(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(PurchaseStatus status);
}
