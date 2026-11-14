package com.waremanager.repository;

import com.waremanager.model.DailyTemperature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyTemperatureRepository extends JpaRepository<DailyTemperature, Long> {

    List<DailyTemperature> findAllByOrderByReadingDateAsc();

    Optional<DailyTemperature> findByReadingDate(LocalDate readingDate);

    /** The 14 most recent readings, newest first. */
    List<DailyTemperature> findTop14ByOrderByReadingDateDesc();
}
