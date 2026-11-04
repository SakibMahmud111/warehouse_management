package com.waremanager.repository;
import com.waremanager.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product,Long> { boolean existsBySku(String sku); }
