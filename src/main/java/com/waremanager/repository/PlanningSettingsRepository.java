package com.waremanager.repository;

import com.waremanager.model.PlanningSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningSettingsRepository extends JpaRepository<PlanningSettings, Long> {
}
