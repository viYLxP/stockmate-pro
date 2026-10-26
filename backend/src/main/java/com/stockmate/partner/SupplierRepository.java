package com.stockmate.partner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    // A blank search returns everything; otherwise match name, phone, email or city (case-insensitive).
    // البحث الفاضي يرجّع الكل، وإلا نطابق الاسم أو الجوال أو البريد أو المدينة بدون حساسية للأحرف.
    @Query("""
            select s from Supplier s
            where :q = '' or lower(s.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(s.phone, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(s.email, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(s.city, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Supplier> search(@Param("q") String q, Pageable pageable);
}
