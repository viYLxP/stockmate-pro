package com.stockmate.stock;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByProductId(Long productId);

    /**
     * Filtered movement history. Null parameters mean "no filter".
     * سجل الحركات مع فلاتر. القيم null تعني "بدون فلتر".
     *
     * <p>{@code @EntityGraph} loads the product in the same query, so listing 20 movements
     * costs one SQL query instead of 21.</p>
     * <p>{@code @EntityGraph} يحمّل المنتج بنفس الاستعلام، فعرض 20 حركة يكلّف استعلامًا واحدًا بدل 21.</p>
     */
    @EntityGraph(attributePaths = "product")
    @Query("""
            select m from StockMovement m
            where (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
              and (cast(:from as timestamp) is null or m.createdAt >= :from)
              and (cast(:to as timestamp) is null or m.createdAt < :to)
            """)
    Page<StockMovement> search(@Param("productId") Long productId,
                               @Param("type") MovementType type,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    @EntityGraph(attributePaths = "product")
    List<StockMovement> findTop8ByOrderByCreatedAtDescIdDesc();
}
