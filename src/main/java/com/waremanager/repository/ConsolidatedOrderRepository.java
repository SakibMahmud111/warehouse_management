package com.waremanager.repository;
import com.waremanager.model.ConsolidatedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ConsolidatedOrderRepository extends JpaRepository<ConsolidatedOrder, Long> {
    List<ConsolidatedOrder> findAllByOrderByCreatedAtDesc();
}
