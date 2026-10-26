package com.stockmate.partner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Same search rules as suppliers.
    // نفس قواعد البحث في الموردين.
    @Query("""
            select c from Customer c
            where :q = '' or lower(c.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.phone, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.email, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.city, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Customer> search(@Param("q") String q, Pageable pageable);
}
