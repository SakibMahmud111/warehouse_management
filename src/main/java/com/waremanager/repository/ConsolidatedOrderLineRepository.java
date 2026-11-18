package com.waremanager.repository;
import com.waremanager.model.ConsolidatedOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ConsolidatedOrderLineRepository extends JpaRepository<ConsolidatedOrderLine, Long> {
    List<ConsolidatedOrderLine> findByConsolidatedOrderIdOrderByIdAsc(Long orderId);
    boolean existsBySuggestionId(Long suggestionId);
}
