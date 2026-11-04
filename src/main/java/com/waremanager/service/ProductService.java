package com.waremanager.service;
import com.waremanager.model.Product;
import com.waremanager.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository){this.repository=repository;}
    @Transactional(readOnly=true) public List<Product> all(){return repository.findAll();}
    @Transactional(readOnly=true) public long count(){return repository.count();}
    @Transactional public void add(Product product){
        if(repository.existsBySku(product.getSku())) throw new IllegalArgumentException("SKU already exists");
        repository.save(product);
    }
}
