package com.stockmate.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    // Returns each category with how many products it has, in a single query (avoids N+1 queries).
    // يرجّع كل تصنيف مع عدد منتجاته في استعلام واحد (نتجنب مشكلة N+1).
    @Query("""
            select c as category, count(p.id) as productCount
            from Category c left join Product p on p.category = c
            group by c
            order by c.name
            """)
    List<CategoryWithCount> findAllWithProductCount();

    interface CategoryWithCount {
        Category getCategory();

        long getProductCount();
    }
}
