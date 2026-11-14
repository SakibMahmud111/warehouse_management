package com.waremanager.repository;

import com.waremanager.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    Optional<Supplier> findByNameIgnoreCase(String name);
}
