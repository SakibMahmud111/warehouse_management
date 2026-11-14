package com.waremanager.service;

import com.waremanager.model.Product;
import com.waremanager.model.Supplier;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final LeadTimeService leadTimes;

    public ProductService(ProductRepository repository, LeadTimeService leadTimes) {
        this.repository = repository;
        this.leadTimes = leadTimes;
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

    /** @param supplierId the chosen supplier, or null for none (F6) */
    @Transactional
    public void add(Product product, Long supplierId) {
        product.setSku(product.getSku().trim());
        if (repository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("SKU already exists");
        }
        product.setId(null);
        product.setQuantityOnOrder(0);
        product.setSupplier(leadTimes.findSupplier(supplierId));
        repository.save(product);
    }

    /**
     * Copies the editable fields from the form onto the saved product.
     * "Quantity on order" is not on the form: it is changed only by the F3 suggestion buttons.
     */
    @Transactional
    public void update(Long id, Product form, Long supplierId) {
        Product product = find(id);
        Supplier supplier = leadTimes.findSupplier(supplierId);
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
        product.setMinOrderQty(form.getMinOrderQty());
        product.setCurrentOrderQty(form.getCurrentOrderQty());
        product.setSupplier(supplier);
    }
}
