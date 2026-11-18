package com.waremanager.service;

import com.waremanager.model.Product;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Product> all() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public Product find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    @Transactional
    public void add(Product product) {
        product.setSku(product.getSku().trim());
        if (repository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("SKU already exists");
        }
        product.setId(null);
        repository.save(product);
    }

    /** Copies the editable fields from the form onto the saved product. */
    @Transactional
    public void update(Long id, Product form) {
        Product product = find(id);
        String sku = form.getSku().trim();
        if (!sku.equals(product.getSku()) && repository.existsBySku(sku)) {
            throw new IllegalArgumentException("SKU already exists");
        }
        product.setName(form.getName());
        product.setSku(sku);
        product.setQuantity(form.getQuantity());
        product.setCategory(form.getCategory());
        product.setSeasonal(form.isSeasonal());
        product.setUnitCost(form.getUnitCost());
        product.setSellingPrice(form.getSellingPrice());
        product.setClearancePrice(form.getClearancePrice());
        product.setLeadTimeDays(form.getLeadTimeDays());
        product.setLeadTimeStdDays(form.getLeadTimeStdDays());
    }
}
