package com.waremanager.repository;
import com.waremanager.model.SalesOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {
    List<SalesOrderLine> findBySalesOrderIdOrderByIdAsc(Long orderId);
    List<SalesOrderLine> findAllByOrderByIdAsc();
}
