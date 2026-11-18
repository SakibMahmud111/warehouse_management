package com.waremanager.repository;
import com.waremanager.model.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findAllByOrderByCreatedAtAscIdAsc();
}
