package com.waremanager.service;

import java.time.LocalDate;

/** One finished winter, measured from the daily temperature records (used by F1). */
public class PastWinter {

    private final int seasonYear;
    private final int coldDays;
    private final LocalDate firstColdDay;
    private final LocalDate lastColdDay;
    private final Double octoberAverage;

    public PastWinter(int seasonYear, int coldDays, LocalDate firstColdDay, LocalDate lastColdDay, Double octoberAverage) {
        this.seasonYear = seasonYear;
        this.coldDays = coldDays;
        this.firstColdDay = firstColdDay;
        this.lastColdDay = lastColdDay;
        this.octoberAverage = octoberAverage;
    }

    /** The year the winter starts in, e.g. 2025 for the winter of 2025-26. */
    public int getSeasonYear() { return seasonYear; }

    /** For example "2025–26". */
    public String getLabel() {
        return seasonYear + "–" + String.format("%02d", (seasonYear + 1) % 100);
    }

    /** Number of cold days (daily average at or below the cold-day threshold) between 1 Nov and 31 Mar. */
    public int getColdDays() { return coldDays; }

    /** First cold day, or null if the winter had none. */
    public LocalDate getFirstColdDay() { return firstColdDay; }

    /** Last cold day, or null if the winter had none. */
    public LocalDate getLastColdDay() { return lastColdDay; }

    /** Average temperature of the October before this winter, or null if October was not recorded. */
    public Double getOctoberAverage() { return octoberAverage; }
}
