package com.stockmate.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextNumber();

    boolean existsByCustomerId(Long customerId);

    @Query("select count(l) > 0 from SalesOrderLine l where l.product.id = :productId")
    boolean existsLineWithProduct(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    Optional<SalesOrder> findWithLinesById(Long id);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product", "lines.product.category"})
    Optional<SalesOrder> findWithLinesByNumber(String number);

    // A customer's own shop orders, newest first. / طلبات العميل نفسه من المتجر، الأحدث أول.
    @EntityGraph(attributePaths = {"lines", "lines.product"})
    @Query("""
            select distinct o from SalesOrder o
            where o.customer.user.id = :userId and o.channel = com.stockmate.sales.SalesChannel.ONLINE
            order by o.createdAt desc, o.id desc
            """)
    List<SalesOrder> findShopOrdersOfUser(@Param("userId") Long userId);

    // Online orders whose payment time ran out while still unpaid. / طلبات أونلاين خلصت مهلة دفعها وهي ما انْدفعت.
    @Query("""
            select o.id from SalesOrder o
            where o.payBy < :now and o.status = com.stockmate.sales.SalesStatus.CONFIRMED
              and o.paymentStatus = com.stockmate.sales.PaymentStatus.UNPAID
            """)
    List<Long> findExpiredUnpaid(@Param("now") Instant now);

    @Query("""
            select count(o) from SalesOrder o
            where o.channel = com.stockmate.sales.SalesChannel.ONLINE
              and o.status = com.stockmate.sales.SalesStatus.CONFIRMED and o.fulfilment = :fulfilment
            """)
    long countOpenOnline(@Param("fulfilment") Fulfilment fulfilment);

    @EntityGraph(attributePaths = "customer")
    @Query("""
            select o from SalesOrder o
            where (:status is null or o.status = :status)
              and (:channel is null or o.channel = :channel)
              and (:customerId is null or o.customer.id = :customerId)
              and (:q = '' or lower(o.number) like lower(concat('%', :q, '%'))
                   or lower(o.customer.name) like lower(concat('%', :q, '%')))
            """)
    Page<SalesOrder> search(@Param("status") SalesStatus status, @Param("channel") SalesChannel channel,
                            @Param("customerId") Long customerId,
                            @Param("q") String q, Pageable pageable);

    // Revenue (incl. VAT) of orders confirmed in [from, to) that are still confirmed (cancelled ones excluded).
    // إيراد الطلبات المؤكدة خلال [from, to) شامل الضريبة، والطلبات الملغية مستبعدة.
    @Query("""
            select coalesce(sum(o.total), 0) from SalesOrder o
            where o.status = com.stockmate.sales.SalesStatus.CONFIRMED
              and o.confirmedAt >= :from and o.confirmedAt < :to
            """)
    BigDecimal confirmedTotal(@Param("from") Instant from, @Param("to") Instant to);

    // Gross profit = Σ(quantity × (sale price − current cost)). Uses current cost as an approximation of COGS.
    // إجمالي الربح = مجموع (الكمية × (سعر البيع − التكلفة الحالية)). نستخدم التكلفة الحالية كتقريب لتكلفة البضاعة المباعة.
    @Query("""
            select coalesce(sum(l.quantity * (l.unitPrice - l.product.costPrice)), 0)
            from SalesOrderLine l
            where l.order.status = com.stockmate.sales.SalesStatus.CONFIRMED
              and l.order.confirmedAt >= :from and l.order.confirmedAt < :to
            """)
    BigDecimal grossProfit(@Param("from") Instant from, @Param("to") Instant to);

    // Confirmed orders since a date, used to build the daily sales chart in Java (database-agnostic grouping by day).
    // الطلبات المؤكدة من تاريخ معيّن، نستخدمها لبناء رسم المبيعات اليومي في Java (تجميع حسب اليوم بدون دوال خاصة بقاعدة البيانات).
    @Query("""
            select o from SalesOrder o
            where o.status = com.stockmate.sales.SalesStatus.CONFIRMED and o.confirmedAt >= :from
            """)
    List<SalesOrder> findConfirmedSince(@Param("from") Instant from);

    // Best sellers by quantity in a period.
    // الأكثر مبيعًا حسب الكمية خلال فترة.
    @Query("""
            select l.product.id as productId, l.product.sku as sku, l.product.name as name,
                   sum(l.quantity) as quantity, sum(l.lineTotal) as revenue
            from SalesOrderLine l
            where l.order.status = com.stockmate.sales.SalesStatus.CONFIRMED and l.order.confirmedAt >= :from
            group by l.product.id, l.product.sku, l.product.name
            order by sum(l.quantity) desc
            """)
    List<TopProduct> topProducts(@Param("from") Instant from, Pageable pageable);

    long countByStatus(SalesStatus status);

    interface TopProduct {
        Long getProductId();

        String getSku();

        String getName();

        Long getQuantity();

        BigDecimal getRevenue();
    }
}
