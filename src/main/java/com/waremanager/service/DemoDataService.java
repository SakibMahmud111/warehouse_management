package com.waremanager.service;

import com.waremanager.model.DailyTemperature;
import com.waremanager.model.Product;
import com.waremanager.model.PurchaseOrder;
import com.waremanager.model.SalesRecord;
import com.waremanager.model.SpecialEvent;
import com.waremanager.model.Supplier;
import com.waremanager.repository.DailyTemperatureRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.PurchaseOrderRepository;
import com.waremanager.repository.SalesRecordRepository;
import com.waremanager.repository.SpecialEventRepository;
import com.waremanager.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Saves the sample data from {@link DemoDataGenerator} so F1-F4 can be demonstrated immediately.
 * Part 1 (products, sales, temperatures, events) runs only when there is no sales history yet.
 * Part 2 (F6 suppliers and past deliveries) runs only when there are no suppliers and no purchase orders,
 * so a database created by the earlier F1-F4 version also gets F6 demo data.
 * Products that existed before are never changed, except that a demo product with no supplier gets its demo supplier.
 */
@Service
public class DemoDataService {

    private final ProductRepository products;
    private final SalesRecordRepository sales;
    private final DailyTemperatureRepository temperatures;
    private final SpecialEventRepository events;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final LeadTimeService leadTimes;

    public DemoDataService(ProductRepository products, SalesRecordRepository sales,
                           DailyTemperatureRepository temperatures, SpecialEventRepository events,
                           SupplierRepository suppliers, PurchaseOrderRepository orders, LeadTimeService leadTimes) {
        this.products = products;
        this.sales = sales;
        this.temperatures = temperatures;
        this.events = events;
        this.suppliers = suppliers;
        this.orders = orders;
        this.leadTimes = leadTimes;
    }

    /** Returns true if any demo data was added. */
    @Transactional
    public boolean seedIfEmpty() {
        boolean addedSales = seedSalesHistory();
        boolean addedDeliveries = seedSuppliersAndDeliveries();
        return addedSales || addedDeliveries;
    }

    /** Part 1: products, weekly sales, temperatures and events (F1-F4, F20). */
    private boolean seedSalesHistory() {
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
            product.setMinOrderQty(demo.minOrderQuantity);
            product.setCurrentOrderQty(demo.currentOrderQuantity);
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

    /** Part 2: suppliers and 8 past deliveries per demo product (F6). */
    private boolean seedSuppliersAndDeliveries() {
        if (suppliers.count() > 0 || orders.count() > 0) {
            return false;
        }
        LocalDate today = LocalDate.now();
        boolean added = false;
        for (DemoDataGenerator.DemoProduct demo : DemoDataGenerator.products()) {
            Product product = products.findBySku(demo.sku).orElse(null);
            if (product == null || demo.supplierName == null) {
                continue;
            }
            Supplier supplier = suppliers.findByNameIgnoreCase(demo.supplierName)
                    .orElseGet(() -> suppliers.save(new Supplier(demo.supplierName)));
            if (product.getSupplier() == null) {
                product.setSupplier(supplier);
            }
            // Oldest first, so each order's "predicted" value only uses deliveries that had already arrived.
            for (DemoDataGenerator.DemoDelivery delivery : DemoDataGenerator.deliveries(demo, today)) {
                PurchaseOrder order = leadTimes.placeOrder(product, delivery.quantity, delivery.orderDate, null);
                order.setReceivedDate(delivery.receivedDate);
                orders.save(order);
            }
            added = true;
        }
        return added;
    }
}
