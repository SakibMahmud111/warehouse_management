package com.waremanager.repository;

import com.waremanager.model.InventoryPolicy;
import com.waremanager.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryPolicyRepository extends JpaRepository<InventoryPolicy, Long> {

    Optional<InventoryPolicy> findByProduct(Product product);

    long countByBelowReorderPointTrue();
}
