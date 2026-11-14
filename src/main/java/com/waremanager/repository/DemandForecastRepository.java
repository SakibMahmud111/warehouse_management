package com.waremanager.repository;

import com.waremanager.model.DemandForecast;
import com.waremanager.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    List<DemandForecast> findByProductOrderByWeekStartAsc(Product product);

    /** Removes a product's old forecasts before the new ones are saved (must run inside a transaction). */
    void deleteByProduct(Product product);
}
