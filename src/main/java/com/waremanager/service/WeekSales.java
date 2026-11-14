package com.waremanager.service;

import java.time.LocalDate;

/** One week of sales history as the forecasting maths sees it (plain data, no database). */
public class WeekSales {

    private final LocalDate weekStart;
    private final double quantity;
    private final boolean eventWeek;

    public WeekSales(LocalDate weekStart, double quantity, boolean eventWeek) {
        this.weekStart = weekStart;
        this.quantity = quantity;
        this.eventWeek = eventWeek;
    }

    /** Monday of the week. */
    public LocalDate getWeekStart() { return weekStart; }
    public double getQuantity() { return quantity; }
    /** True if the week had a holiday or promotion (for example Eid shopping week). */
    public boolean isEventWeek() { return eventWeek; }
}
