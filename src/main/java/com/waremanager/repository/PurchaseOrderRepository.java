package com.waremanager.repository;

import com.waremanager.model.Product;
import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.ReorderSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /** Received orders of one product, oldest order first (F6 training data). */
    List<PurchaseOrder> findByProductAndReceivedDateIsNotNullOrderByOrderDateAscIdAsc(Product product);

    /** Orders of one product still on their way, oldest first (F7 next delivery). */
    List<PurchaseOrder> findByProductAndReceivedDateIsNullOrderByOrderDateAscIdAsc(Product product);

    Optional<PurchaseOrder> findBySuggestion(ReorderSuggestion suggestion);

    List<PurchaseOrder> findByReceivedDateIsNullOrderByOrderDateAscIdAsc();

    List<PurchaseOrder> findTop20ByReceivedDateIsNotNullOrderByReceivedDateDescIdDesc();
}
