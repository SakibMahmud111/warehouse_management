package com.waremanager.service;

import java.time.LocalDate;

/** F1: the forecast for one future week. */
public class WeekForecast {

    private final LocalDate weekStart;
    private final int expectedColdDays;
    private final boolean eventWeek;
    private final double quantity;

    public WeekForecast(LocalDate weekStart, int expectedColdDays, boolean eventWeek, double quantity) {
        this.weekStart = weekStart;
        this.expectedColdDays = expectedColdDays;
        this.eventWeek = eventWeek;
        this.quantity = quantity;
    }

    /** Monday of the week. */
    public LocalDate getWeekStart() { return weekStart; }
    /** Cold days the winter prediction expects in this week. */
    public int getExpectedColdDays() { return expectedColdDays; }
    /** True if a holiday or promotion falls in this week. */
    public boolean isEventWeek() { return eventWeek; }
    /** Forecast units sold in this week. */
    public double getQuantity() { return quantity; }
}
