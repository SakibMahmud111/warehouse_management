package com.waremanager.service;

import com.waremanager.model.PlanningSettings;
import com.waremanager.repository.PlanningSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and saves the single row of planning settings (created with defaults the first time). */
@Service
public class SettingsService {

    private final PlanningSettingsRepository repository;

    public SettingsService(PlanningSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PlanningSettings get() {
        return repository.findById(PlanningSettings.SINGLE_ROW_ID)
                .orElseGet(() -> repository.save(new PlanningSettings()));
    }

    @Transactional
    public void update(PlanningSettings form) {
        PlanningSettings settings = get();
        settings.setOrderingCost(form.getOrderingCost());
        settings.setHoldingRatePercent(form.getHoldingRatePercent());
        settings.setServiceLevelPercent(form.getServiceLevelPercent());
        settings.setColdDayThreshold(form.getColdDayThreshold());
        settings.setDefaultStockDays(form.getDefaultStockDays());
        settings.setStockoutAlertPercent(form.getStockoutAlertPercent());
        settings.setAnomalyThresholdPercent(form.getAnomalyThresholdPercent());
    }
}
