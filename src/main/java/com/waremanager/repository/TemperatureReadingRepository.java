package com.waremanager.repository;
import com.waremanager.model.TemperatureReading;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TemperatureReadingRepository extends JpaRepository<TemperatureReading, Long> {
    List<TemperatureReading> findTop30ByOrderByMeasuredAtDesc();
}
