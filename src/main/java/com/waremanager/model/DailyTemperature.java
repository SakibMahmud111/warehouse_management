package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** The average outdoor temperature of one day. F1 uses it to measure and predict winter. */
@Entity
@Table(name = "daily_temperatures")
public class DailyTemperature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reading_date", nullable = false, unique = true)
    private LocalDate readingDate;

    /** Daily average in degrees Celsius. */
    @Column(name = "average_c", nullable = false)
    private double averageC;

    protected DailyTemperature() {
    }

    public DailyTemperature(LocalDate readingDate, double averageC) {
        this.readingDate = readingDate;
        this.averageC = averageC;
    }

    public Long getId() { return id; }
    public LocalDate getReadingDate() { return readingDate; }
    public double getAverageC() { return averageC; }
    public void setAverageC(double averageC) { this.averageC = averageC; }
}
