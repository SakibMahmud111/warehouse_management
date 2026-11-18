package com.waremanager.service;

import com.waremanager.model.DailyTemperature;
import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import com.waremanager.model.SpecialEvent;
import com.waremanager.repository.DailyTemperatureRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.SalesRecordRepository;
import com.waremanager.repository.SpecialEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Saves the sample data from {@link DemoDataGenerator} so F1 and F2 can be demonstrated immediately.
 * It runs only when there is no sales history yet. Products that existed before are never changed.
 */
@Service
public class DemoDataService {

    private final ProductRepository products;
    private final SalesRecordRepository sales;
    private final DailyTemperatureRepository temperatures;
    private final SpecialEventRepository events;

    public DemoDataService(ProductRepository products, SalesRecordRepository sales,
                           DailyTemperatureRepository temperatures, SpecialEventRepository events) {
        this.products = products;
        this.sales = sales;
        this.temperatures = temperatures;
        this.events = events;
    }

    /** Returns true if any demo data was added. */
    @Transactional
    public boolean seedIfEmpty() {
        if (sales.count() > 0) {
            return false;
        }
        LocalDate today = LocalDate.now();
        SortedMap<LocalDate, Double> generatedTemperatures = DemoDataGenerator.temperatures(today);

        if (temperatures.count() == 0) {
            List<DailyTemperature> rows = new ArrayList<>();
            for (Map.Entry<LocalDate, Double> reading : generatedTemperatures.entrySet()) {
                rows.add(new DailyTemperature(reading.getKey(), reading.getValue()));
            }
            temperatures.saveAll(rows);
        }

        List<DemoDataGenerator.DemoEvent> demoEvents = DemoDataGenerator.events();
        if (events.count() == 0) {
            for (DemoDataGenerator.DemoEvent event : demoEvents) {
                events.save(new SpecialEvent(event.name, event.start, event.end));
            }
        }

        for (DemoDataGenerator.DemoProduct demo : DemoDataGenerator.products()) {
            if (products.existsBySku(demo.sku)) {
                continue;   // never touch a product the user already has
            }
            Product product = new Product();
            product.setSku(demo.sku);
            product.setName(demo.name);
            product.setCategory(demo.category);
            product.setSeasonal(demo.seasonal);
            product.setUnitCost(demo.unitCost);
            product.setSellingPrice(demo.sellingPrice);
            product.setClearancePrice(demo.clearancePrice);
            product.setQuantity(demo.stockOnHand);
            product.setLeadTimeDays(demo.leadTimeDays);
            product.setLeadTimeStdDays(demo.leadTimeStdDays);
            products.save(product);

            List<SalesRecord> rows = new ArrayList<>();
            for (WeekSales week : DemoDataGenerator.weeklySales(demo, generatedTemperatures,
                    DemoDataGenerator.COLD_THRESHOLD, demoEvents, today)) {
                rows.add(new SalesRecord(product, week.getWeekStart(), (int) week.getQuantity(), week.isEventWeek()));
            }
            sales.saveAll(rows);
        }
        return true;
    }
}
