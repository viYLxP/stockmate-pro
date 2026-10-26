package com.stockmate.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySkuIgnoreCase(String sku);

    long countByCategoryId(Long categoryId);

    boolean existsBySupplierId(Long supplierId);

    // ----- Dashboard aggregates / تجميعات لوحة التحكم -----

    // Inventory value at cost = Σ(quantity × cost price) of active products.
    // قيمة المخزون بسعر التكلفة = مجموع (الكمية × سعر التكلفة) للمنتجات النشطة.
    @Query("select coalesce(sum(p.quantity * p.costPrice), 0) from Product p where p.active = true")
    BigDecimal inventoryCostValue();

    // Potential sales value = Σ(quantity × sale price).
    // قيمة البيع المتوقعة = مجموع (الكمية × سعر البيع).
    @Query("select coalesce(sum(p.quantity * p.salePrice), 0) from Product p where p.active = true")
    BigDecimal inventoryRetailValue();

    long countByActiveTrue();

    @Query("select count(p) from Product p where p.active = true and p.quantity > 0 and p.quantity <= p.reorderLevel")
    long countLowStock();

    @Query("select count(p) from Product p where p.active = true and p.quantity = 0")
    long countOutOfStock();

    // Products needing a reorder, most urgent (lowest quantity) first.
    // المنتجات اللي تحتاج إعادة طلب، الأكثر إلحاحًا (الأقل كمية) أولًا.
    @Query("select p from Product p where p.active = true and p.quantity <= p.reorderLevel order by p.quantity asc, p.name asc")
    List<Product> findReorderList(Pageable pageable);
}
