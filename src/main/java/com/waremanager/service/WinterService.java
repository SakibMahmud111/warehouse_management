package com.waremanager.service;

import com.waremanager.model.DailyTemperature;
import com.waremanager.repository.DailyTemperatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * F1: loads the temperature records and asks {@link WinterForecaster} for this winter's prediction.
 * The regression is trained by {@link ModelTrainer} (Python + scikit-learn, or the Java backup).
 */
@Service
public class WinterService {

    private final DailyTemperatureRepository temperatures;
    private final ModelTrainer models;

    public WinterService(DailyTemperatureRepository temperatures, ModelTrainer models) {
        this.temperatures = temperatures;
        this.models = models;
    }

    @Transactional(readOnly = true)
    public WinterOutlook outlook(double coldThreshold, LocalDate today) {
        SortedMap<LocalDate, Double> readings = new TreeMap<>();
        for (DailyTemperature reading : temperatures.findAllByOrderByReadingDateAsc()) {
            readings.put(reading.getReadingDate(), reading.getAverageC());
        }
        return WinterForecaster.predict(readings, today, coldThreshold, models);
    }

    /** Adds a reading, or corrects it if that date already has one. */
    @Transactional
    public void saveReading(LocalDate date, double averageC) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Temperature readings cannot be in the future");
        }
        if (averageC < -10 || averageC > 50) {
            throw new IllegalArgumentException("Temperature must be between -10 and 50 °C");
        }
        DailyTemperature reading = temperatures.findByReadingDate(date).orElse(null);
        if (reading == null) {
            temperatures.save(new DailyTemperature(date, averageC));
        } else {
            reading.setAverageC(averageC);
        }
    }

    /** The 14 most recent readings, newest first. */
    @Transactional(readOnly = true)
    public List<DailyTemperature> recentReadings() {
        return temperatures.findTop14ByOrderByReadingDateDesc();
    }
}
