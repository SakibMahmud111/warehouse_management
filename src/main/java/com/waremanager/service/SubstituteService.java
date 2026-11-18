package com.waremanager.service;

import com.waremanager.model.*;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.SalesOrderLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** F16: match every required product attribute and check actual unreserved stock. */
@Service
public class SubstituteService {
    public record Candidate(Product product, int available, int score) {}
    private final ProductRepository products;
    private final SalesOrderLineRepository lines;
    private final StockService stock;

    public SubstituteService(ProductRepository products, SalesOrderLineRepository lines, StockService stock) {
        this.products = products;
        this.lines = lines;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public SalesOrderLine line(Long id) {
        return lines.findById(id).orElseThrow(() -> new IllegalArgumentException("Order line not found"));
    }

    @Transactional(readOnly = true)
    public List<Candidate> alternatives(Long lineId) {
        SalesOrderLine line = line(lineId);
        Product original = line.getProduct();
        Map<String, String> requirements = parse(original.getKeyAttributes());
        List<Candidate> results = new ArrayList<>();
        for (Product candidate : products.findAllByOrderByNameAsc()) {
            if (candidate.getId().equals(original.getId())
                    || !candidate.getCategory().equalsIgnoreCase(original.getCategory())) continue;
            Map<String, String> attributes = parse(candidate.getKeyAttributes());
            if (!requirements.entrySet().stream().allMatch(x -> x.getValue().equals(attributes.get(x.getKey()))))
                continue;
            int available = stock.allocatable(candidate.getId(), line.getMinShelfLifeDays());
            if (available < line.getQuantity()) continue;
            // All required attributes match; prefer more specifically described alternatives, then higher stock.
            results.add(new Candidate(candidate, available, attributes.size()));
        }
        results.sort(Comparator.comparingInt(Candidate::score).reversed()
            .thenComparing(Comparator.comparingInt(Candidate::available).reversed()));
        return results;
    }

    @Transactional
    public void accept(Long lineId, Long candidateId) {
        SalesOrderLine line = line(lineId);
        if (line.getSalesOrder().getStatus() != SalesOrder.Status.DRAFT || line.getReservedQuantity() != 0)
            throw new IllegalArgumentException("A substitute may be chosen only before confirming the order");
        Product selected = alternatives(lineId).stream()
            .filter(x -> x.product().getId().equals(candidateId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Alternative is unavailable or violates required attributes"))
            .product();
        line.setProduct(selected);
    }

    private Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return values;
        for (String pair : text.split(",")) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank())
                values.put(parts[0].trim().toLowerCase(Locale.ROOT), parts[1].trim().toLowerCase(Locale.ROOT));
        }
        return values;
    }
}
