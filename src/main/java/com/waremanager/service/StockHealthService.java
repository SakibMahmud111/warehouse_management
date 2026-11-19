package com.waremanager.service;

import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StockHealthService {

    public static final int DEFAULT_DEAD_DAYS = 90, DEFAULT_COVER_DAYS = 120;

    public record Row(Product product, StockHealthClassifier.Result result) {}
    public record Summary(int problemCount, double valueTiedUp) {}

    private final ProductRepository products;
    private final SalesRecordRepository sales;

    public StockHealthService(ProductRepository products, SalesRecordRepository sales) {
        this.products = products;
        this.sales = sales;
    }

    /** Every product classified, most money tied up first. */
    @Transactional(readOnly = true)
    public List<Row> analyse(int deadDays, int coverDays) {
        LocalDate today = LocalDate.now();
        List<Row> rows = new ArrayList<>();
        for (Product p : products.findAll()) {
            List<SalesRecord> history = sales.findByProductOrderByWeekStartDesc(p);
            double avgWeekly = history.stream().limit(8).mapToInt(SalesRecord::getQuantity).average().orElse(0);
            Integer daysSince = history.stream().filter(s -> s.getQuantity() > 0).findFirst()
                    .map(s -> (int) Math.max(0, ChronoUnit.DAYS.between(s.getWeekStart().plusDays(6), today)))
                    .orElse(null);
            rows.add(new Row(p, StockHealthClassifier.classify(
                    p.getQuantity(), avgWeekly, daysSince, p.getUnitCost(), deadDays, coverDays)));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.result().valueTiedUp()).reversed());
        return rows;
    }

    /** Dashboard card: dead + overstock count and their value. */
    @Transactional(readOnly = true)
    public Summary summary() {
        List<Row> bad = analyse(DEFAULT_DEAD_DAYS, DEFAULT_COVER_DAYS).stream()
                .filter(r -> r.result().status() == StockHealthClassifier.Status.DEAD
                          || r.result().status() == StockHealthClassifier.Status.OVERSTOCK).toList();
        return new Summary(bad.size(), bad.stream().mapToDouble(r -> r.result().valueTiedUp()).sum());
    }
}