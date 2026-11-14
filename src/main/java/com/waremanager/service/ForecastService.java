package com.waremanager.service;

import com.waremanager.model.DemandForecast;
import com.waremanager.model.Product;
import com.waremanager.model.SalesRecord;
import com.waremanager.model.SpecialEvent;
import com.waremanager.repository.DemandForecastRepository;
import com.waremanager.repository.ProductRepository;
import com.waremanager.repository.SalesRecordRepository;
import com.waremanager.repository.SpecialEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F1 Demand Forecasting: learns each product's weekly sales model (see {@link DemandForecaster}),
 * saves 12 weeks of forecasts, and manages the inputs (weekly sales, holidays and promotions).
 */
@Service
public class ForecastService {

    private final ProductRepository products;
    private final SalesRecordRepository sales;
    private final DemandForecastRepository forecasts;
    private final SpecialEventRepository events;
    private final ModelTrainer models;

    public ForecastService(ProductRepository products, SalesRecordRepository sales,
                           DemandForecastRepository forecasts, SpecialEventRepository events, ModelTrainer models) {
        this.products = products;
        this.sales = sales;
        this.forecasts = forecasts;
        this.events = events;
        this.models = models;
    }

    /** Monday of the week that contains {@code day}. */
    public static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Learns the demand model for one product from its sales history. Nothing is saved. */
    @Transactional(readOnly = true)
    public DemandModel learnModel(Product product, WinterOutlook outlook) {
        List<WeekSales> history = new ArrayList<>();
        for (SalesRecord record : sales.findByProductOrderByWeekStartAsc(product)) {
            history.add(new WeekSales(record.getWeekStart(), record.getQuantity(), record.isEventWeek()));
        }
        return DemandForecaster.fit(history, product.isSeasonal(), outlook, models);
    }

    /** Planning run, step F1: forecasts the next 12 weeks and saves them, replacing the old forecasts. */
    @Transactional
    public List<WeekForecast> forecastAndSave(Product product, DemandModel model, WinterOutlook outlook, LocalDate today) {
        LocalDate thisMonday = mondayOf(today);
        List<WeekForecast> weeks = DemandForecaster.forecast(model, thisMonday, DemandForecaster.FORECAST_WEEKS,
                outlook, eventMondays(thisMonday, DemandForecaster.FORECAST_WEEKS));

        forecasts.deleteByProduct(product);
        LocalDateTime now = LocalDateTime.now();
        List<DemandForecast> rows = new ArrayList<>();
        for (WeekForecast week : weeks) {
            rows.add(new DemandForecast(product, week.getWeekStart(), week.getQuantity(),
                    week.getExpectedColdDays(), week.isEventWeek(), now));
        }
        forecasts.saveAll(rows);
        return weeks;
    }

    /** Data for the F1 page: model, last 52 weeks of sales and the saved forecasts. */
    @Transactional(readOnly = true)
    public ProductForecast productForecast(Product product, WinterOutlook outlook) {
        List<SalesRecord> records = sales.findByProductOrderByWeekStartAsc(product);
        List<WeekSales> lastYear = new ArrayList<>();
        for (SalesRecord record : records.subList(Math.max(0, records.size() - 52), records.size())) {
            lastYear.add(new WeekSales(record.getWeekStart(), record.getQuantity(), record.isEventWeek()));
        }
        return new ProductForecast(product, learnModel(product, outlook), lastYear,
                forecasts.findByProductOrderByWeekStartAsc(product));
    }

    // ---------------------------------------------------------------- inputs

    /** Records (or corrects) the units sold in the week that contains {@code anyDayOfWeek}. */
    @Transactional
    public void recordWeeklySales(Long productId, LocalDate anyDayOfWeek, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (anyDayOfWeek == null) {
            throw new IllegalArgumentException("Week is required");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Units sold must not be negative");
        }
        LocalDate monday = mondayOf(anyDayOfWeek);
        if (monday.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Sales cannot be recorded for a future week");
        }
        boolean eventWeek = isEventWeek(monday, events.findAll());
        SalesRecord record = sales.findByProductAndWeekStart(product, monday).orElse(null);
        if (record == null) {
            sales.save(new SalesRecord(product, monday, quantity, eventWeek));
        } else {
            record.setQuantity(quantity);
            record.setEventWeek(eventWeek);
        }
    }

    @Transactional(readOnly = true)
    public List<SpecialEvent> allEvents() {
        return events.findAllByOrderByStartDateAsc();
    }

    @Transactional
    public void addEvent(String name, LocalDate startDate, LocalDate endDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("Event name must be at most 100 characters");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before the start date");
        }
        events.save(new SpecialEvent(name.trim(), startDate, endDate));
        refreshEventFlags();
    }

    @Transactional
    public void deleteEvent(Long id) {
        events.deleteById(id);
        events.flush();
        refreshEventFlags();
    }

    /** Re-marks which past sales weeks were event weeks, after the event list changed. */
    private void refreshEventFlags() {
        List<SpecialEvent> all = events.findAll();
        for (SalesRecord record : sales.findAll()) {
            record.setEventWeek(isEventWeek(record.getWeekStart(), all));
        }
    }

    private Set<LocalDate> eventMondays(LocalDate firstMonday, int weeks) {
        List<SpecialEvent> all = events.findAll();
        Set<LocalDate> mondays = new HashSet<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate monday = firstMonday.plusWeeks(i);
            if (isEventWeek(monday, all)) {
                mondays.add(monday);
            }
        }
        return mondays;
    }

    private static boolean isEventWeek(LocalDate monday, List<SpecialEvent> all) {
        for (SpecialEvent event : all) {
            if (event.overlapsWeek(monday)) {
                return true;
            }
        }
        return false;
    }
}
