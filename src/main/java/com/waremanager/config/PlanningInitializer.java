package com.waremanager.config;

import com.waremanager.service.PlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Recalculates F1-F4 on every startup, so forecasts always start from today's date. */
@Component
@Order(3)
public class PlanningInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanningInitializer.class);

    private final PlanningService planning;

    public PlanningInitializer(PlanningService planning) {
        this.planning = planning;
    }

    @Override
    public void run(String... args) {
        int count = planning.recalculateAll();
        log.info("Planning run finished for {} products (forecast, safety stock, order quantity, reorder points).", count);
    }
}
