package com.waremanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "temperature_readings")
public class TemperatureReading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String binCode;
    @Column(nullable = false)
    private double celsius;
    @Column(nullable = false)
    private LocalDateTime measuredAt;
    protected TemperatureReading() {}
    public TemperatureReading(String binCode, double celsius, LocalDateTime measuredAt) {
        this.binCode = binCode;
        this.celsius = celsius;
        this.measuredAt = measuredAt;
    }
    public Long getId() { return id; }
    public String getBinCode() { return binCode; }
    public double getCelsius() { return celsius; }
    public LocalDateTime getMeasuredAt() { return measuredAt; }
}
