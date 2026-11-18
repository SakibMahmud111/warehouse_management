package com.waremanager.config;

import com.waremanager.service.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On startup, adds sample products, sales history and temperatures for F1-F4 if there is no
 * sales history yet. Turn it off with app.demo-data.enabled=false (or DEMO_DATA=false).
 */
@Component
@Order(2)
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoDataService demoData;
    private final boolean enabled;

    public DemoDataInitializer(DemoDataService demoData, @Value("${app.demo-data.enabled:true}") boolean enabled) {
        this.demoData = demoData;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (enabled && demoData.seedIfEmpty()) {
            log.info("Added demo products, 3 years of weekly sales and 8 years of daily temperatures.");
        }
    }
}
