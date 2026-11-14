package com.waremanager.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * F1: the prediction for the current (or next) winter, made by {@link WinterForecaster}.
 *
 * It answers: how many cold days will this winter have, how many are left,
 * and on which dates do we expect them? Forecasts for winter products use it.
 */
public class WinterOutlook {

    private final int seasonYear;
    private final double coldThreshold;
    private final String method;
    private final int predictedDays;
    private final double predictionStd;
    private final int lowDays;
    private final int highDays;
    private final int normalDays;
    private final int coldDaysSoFar;
    private final int remainingDays;
    private final boolean finished;
    private final LocalDate expectedStart;
    private final LocalDate expectedEnd;
    private final Double octoberAverage;
    private final double rSquared;
    private final List<PastWinter> pastWinters;
    private final LocalDate lastReadingDate;
    private final Set<LocalDate> coldDates;

    public WinterOutlook(int seasonYear, double coldThreshold, String method, int predictedDays, double predictionStd,
                         int lowDays, int highDays, int normalDays, int coldDaysSoFar, int remainingDays,
                         boolean finished, LocalDate expectedStart, LocalDate expectedEnd, Double octoberAverage,
                         double rSquared, List<PastWinter> pastWinters, LocalDate lastReadingDate,
                         Set<LocalDate> coldDates) {
        this.seasonYear = seasonYear;
        this.coldThreshold = coldThreshold;
        this.method = method;
        this.predictedDays = predictedDays;
        this.predictionStd = predictionStd;
        this.lowDays = lowDays;
        this.highDays = highDays;
        this.normalDays = normalDays;
        this.coldDaysSoFar = coldDaysSoFar;
        this.remainingDays = remainingDays;
        this.finished = finished;
        this.expectedStart = expectedStart;
        this.expectedEnd = expectedEnd;
        this.octoberAverage = octoberAverage;
        this.rSquared = rSquared;
        this.pastWinters = pastWinters;
        this.lastReadingDate = lastReadingDate;
        this.coldDates = coldDates;
    }

    // ---------- Used by the forecasts ----------

    /** Cold days that were actually recorded in the 7 days starting on {@code monday}. */
    public int observedColdDaysInWeek(LocalDate monday) {
        int count = 0;
        for (int i = 0; i < 7; i++) {
            if (coldDates.contains(monday.plusDays(i))) {
                count++;
            }
        }
        return count;
    }

    /** Predicted cold days from {@code from} to {@code to} (both included). */
    public int expectedColdDaysBetween(LocalDate from, LocalDate to) {
        if (remainingDays <= 0 || expectedStart == null) {
            return 0;
        }
        LocalDate start = from.isAfter(expectedStart) ? from : expectedStart;
        LocalDate end = to.isBefore(expectedEnd) ? to : expectedEnd;
        if (end.isBefore(start)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Predicted cold days in the 7 days starting on {@code monday}. */
    public int expectedColdDaysInWeek(LocalDate monday) {
        return expectedColdDaysBetween(monday, monday.plusDays(6));
    }

    // ---------- Shown on the pages ----------

    public int getSeasonYear() { return seasonYear; }

    /** For example "Winter 2026–27". */
    public String getSeasonLabel() {
        return "Winter " + seasonYear + "–" + String.format("%02d", (seasonYear + 1) % 100);
    }

    public double getColdThreshold() { return coldThreshold; }
    public String getMethod() { return method; }

    /** Predicted total cold days for the whole winter. */
    public int getPredictedDays() { return predictedDays; }

    /** Typical size of the prediction error, in days. */
    public double getPredictionStd() { return predictionStd; }

    /** About 80% of winters should fall between low and high. */
    public int getLowDays() { return lowDays; }
    public int getHighDays() { return highDays; }

    /** Average length of the past winters in the data. */
    public int getNormalDays() { return normalDays; }

    /** Predicted days minus normal days (negative = shorter winter than normal). */
    public int getDifferenceFromNormal() { return predictedDays - normalDays; }

    public int getColdDaysSoFar() { return coldDaysSoFar; }
    public int getRemainingDays() { return remainingDays; }

    /** True once at least one cold day has been recorded and the winter is not over. */
    public boolean isInProgress() { return coldDaysSoFar > 0 && !finished; }

    public boolean isFinished() { return finished; }

    /** Predicted first and last cold day still to come (null when no cold days are left). */
    public LocalDate getExpectedStart() { return expectedStart; }
    public LocalDate getExpectedEnd() { return expectedEnd; }

    /** This October's average temperature (the model input), or null if not recorded yet. */
    public Double getOctoberAverage() { return octoberAverage; }

    /** How well the regression line fits past winters (1 = perfect). 0 when regression was not used. */
    public double getRSquared() { return rSquared; }

    public List<PastWinter> getPastWinters() { return pastWinters; }

    /** Longest past winter, used to scale the bars on the page. */
    public int getMaxWinterDays() {
        int max = Math.max(1, predictedDays);
        for (PastWinter winter : pastWinters) {
            max = Math.max(max, winter.getColdDays());
        }
        return max;
    }

    public LocalDate getLastReadingDate() { return lastReadingDate; }

    public boolean hasHistory() { return !pastWinters.isEmpty(); }
}
