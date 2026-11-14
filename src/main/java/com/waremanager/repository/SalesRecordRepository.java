package com.waremanager.repository;

import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    /** Oldest week first: SELECT ... WHERE product_id = ? ORDER BY week_start ASC */
    List<SalesRecord> findByProductOrderByWeekStartAsc(Product product);

    Optional<SalesRecord> findByProductAndWeekStart(Product product, LocalDate weekStart);
}
