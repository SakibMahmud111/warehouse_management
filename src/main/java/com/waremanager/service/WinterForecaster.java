package com.waremanager.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * F1: predicts how long this winter will last, from daily temperature records.
 *
 * Idea in plain words:
 *  1. A "cold day" is a day whose average temperature is at or below the threshold (default 20 °C).
 *  2. For every past winter (1 Nov to 31 Mar) count the cold days, and note how warm the October before it was.
 *  3. Learn a straight line "winter length = a + b × October temperature" from those past winters
 *     (simple linear regression, trained by {@link ModelTrainer}: Python + scikit-learn, or the Java backup).
 *     A warm October usually means a short winter.
 *  4. Put this year's October temperature into the line to predict this winter's length.
 *  5. If there is not enough data, use the average of the last 3 winters instead.
 *  6. Once winter has started, count the cold days that already happened and predict only what is left.
 *
 * Plain Java with no database access: {@link WinterService} loads the temperatures and calls this class.
 */
public final class WinterForecaster {

    /** A winter is only learned from if at least this many of its 151 days were recorded. */
    static final int MIN_RECORDED_DAYS_PER_WINTER = 136;
    /** October counts as recorded if at least this many days have readings. */
    static final int MIN_OCTOBER_READINGS = 20;
    /** Regression needs at least this many past winters. */
    static final int MIN_WINTERS_FOR_REGRESSION = 4;
    /** About 80% of outcomes fall within ±1.28 standard deviations. */
    private static final double Z_80_PERCENT = 1.28;

    private WinterForecaster() {
        // only static methods
    }

    /** The winter season that "today" belongs to: Jan-Mar belong to the winter that started the year before. */
    public static int seasonYearFor(LocalDate today) {
        return today.getMonthValue() <= 3 ? today.getYear() - 1 : today.getYear();
    }

    /** Same as below, using the Java version of the model (for unit tests). */
    public static WinterOutlook predict(SortedMap<LocalDate, Double> temperatures, LocalDate today, double coldThreshold) {
        return predict(temperatures, today, coldThreshold, JavaModelTrainer.INSTANCE);
    }

    public static WinterOutlook predict(SortedMap<LocalDate, Double> temperatures, LocalDate today, double coldThreshold,
                                        ModelTrainer models) {
        int seasonYear = seasonYearFor(today);

        Set<LocalDate> coldDates = new HashSet<>();
        for (Map.Entry<LocalDate, Double> reading : temperatures.entrySet()) {
            if (reading.getValue() <= coldThreshold) {
                coldDates.add(reading.getKey());
            }
        }

        // ----- Step 2: measure every complete past winter -----
        List<PastWinter> pastWinters = new ArrayList<>();
        if (!temperatures.isEmpty()) {
            int firstYear = temperatures.firstKey().getYear() - 1;
            for (int year = firstYear; year < seasonYear; year++) {
                PastWinter winter = measureWinter(temperatures, coldDates, year);
                if (winter != null) {
                    pastWinters.add(winter);
                }
            }
        }

        // ----- Steps 3-5: predict the total length of this winter -----
        Double octoberAverage = octoberAverage(temperatures, seasonYear);
        List<PastWinter> withOctober = new ArrayList<>();
        for (PastWinter winter : pastWinters) {
            if (winter.getOctoberAverage() != null) {
                withOctober.add(winter);
            }
        }

        String method;
        double predicted;
        double predictionStd;
        double rSquared = 0;

        if (withOctober.size() >= MIN_WINTERS_FOR_REGRESSION && octoberAverage != null) {
            double[] x = new double[withOctober.size()];
            double[] y = new double[withOctober.size()];
            for (int i = 0; i < withOctober.size(); i++) {
                x[i] = withOctober.get(i).getOctoberAverage();
                y[i] = withOctober.get(i).getColdDays();
            }
            Statistics.LineFit line = models.fitLine(x, y);
            predicted = line.predict(octoberAverage);
            predictionStd = line.getResidualStd();
            rSquared = line.getRSquared();
            method = String.format("Linear regression: winter days = %.1f %+.1f × October °C (learned from %d winters)",
                    line.getIntercept(), line.getSlope(), withOctober.size());
        } else if (!pastWinters.isEmpty()) {
            double[] lastThree = new double[Math.min(3, pastWinters.size())];
            for (int i = 0; i < lastThree.length; i++) {
                lastThree[i] = pastWinters.get(pastWinters.size() - 1 - i).getColdDays();
            }
            predicted = Statistics.mean(lastThree);
            predictionStd = Statistics.standardDeviation(allColdDays(pastWinters));
            method = octoberAverage == null
                    ? "Average of the last " + lastThree.length + " winters (this October is not fully recorded yet)"
                    : "Average of the last " + lastThree.length + " winters (not enough past winters for regression)";
        } else {
            predicted = 0;
            predictionStd = 0;
            method = "Not enough temperature history to predict the winter";
        }

        predicted = Math.max(0, Math.min(151, predicted));
        if (!pastWinters.isEmpty()) {
            predictionStd = Math.max(predictionStd, 2.0); // never pretend to be more certain than ±2 days
        }

        // ----- Step 6: what has already happened this winter? -----
        LocalDate seasonStart = LocalDate.of(seasonYear, Month.NOVEMBER, 1);
        int coldSoFar = 0;
        for (LocalDate day : coldDates) {
            if (!day.isBefore(seasonStart) && !day.isAfter(today)) {
                coldSoFar++;
            }
        }
        LocalDate lastReading = temperatures.isEmpty() ? null : temperatures.lastKey();
        boolean finished = coldSoFar > 0 && looksFinished(temperatures, coldDates, seasonYear, today);

        int predictedTotal = (int) Math.round(predicted);
        if (finished) {
            predictedTotal = coldSoFar;
        } else {
            predictedTotal = Math.max(predictedTotal, coldSoFar);
        }
        int remaining = finished ? 0 : Math.max(0, predictedTotal - coldSoFar);
        int low = Math.max(coldSoFar, (int) Math.round(predicted - Z_80_PERCENT * predictionStd));
        int high = Math.max(low, (int) Math.round(predicted + Z_80_PERCENT * predictionStd));
        if (finished) {
            low = coldSoFar;
            high = coldSoFar;
        }

        // ----- When are the remaining cold days expected? -----
        LocalDate expectedStart = null;
        LocalDate expectedEnd = null;
        if (remaining > 0) {
            LocalDate firstFutureDay = today;
            if (lastReading != null && !lastReading.isBefore(today)) {
                firstFutureDay = lastReading.plusDays(1);
            }
            LocalDate start;
            if (coldSoFar == 0) {
                // Not started yet: centre the predicted winter on the usual middle of winter.
                LocalDate usualMiddle = seasonStart.plusDays(usualMiddleOffset(pastWinters));
                start = usualMiddle.minusDays(predictedTotal / 2);
            } else {
                start = firstFutureDay;
            }
            if (start.isBefore(firstFutureDay)) {
                start = firstFutureDay;
            }
            expectedStart = start;
            expectedEnd = start.plusDays(remaining - 1L);
        }

        int normalDays = pastWinters.isEmpty() ? 0 : (int) Math.round(Statistics.mean(allColdDays(pastWinters)));

        return new WinterOutlook(seasonYear, coldThreshold, method, predictedTotal, predictionStd, low, high,
                normalDays, coldSoFar, remaining, finished, expectedStart, expectedEnd, octoberAverage, rSquared,
                pastWinters, lastReading, coldDates);
    }

    /** Counts the cold days of the winter starting in {@code year}; null if too few days were recorded. */
    private static PastWinter measureWinter(SortedMap<LocalDate, Double> temperatures, Set<LocalDate> coldDates, int year) {
        LocalDate start = LocalDate.of(year, Month.NOVEMBER, 1);
        LocalDate end = LocalDate.of(year + 1, Month.MARCH, 31);
        SortedMap<LocalDate, Double> window = temperatures.subMap(start, end.plusDays(1));
        if (window.size() < MIN_RECORDED_DAYS_PER_WINTER) {
            return null;
        }
        int cold = 0;
        LocalDate first = null;
        LocalDate last = null;
        for (LocalDate day : window.keySet()) {
            if (coldDates.contains(day)) {
                cold++;
                if (first == null) {
                    first = day;
                }
                last = day;
            }
        }
        return new PastWinter(year, cold, first, last, octoberAverage(temperatures, year));
    }

    /** Average October temperature of the given year, or null if fewer than 20 days were recorded. */
    static Double octoberAverage(SortedMap<LocalDate, Double> temperatures, int year) {
        SortedMap<LocalDate, Double> october = temperatures.subMap(
                LocalDate.of(year, Month.OCTOBER, 1), LocalDate.of(year, Month.NOVEMBER, 1));
        if (october.size() < MIN_OCTOBER_READINGS) {
            return null;
        }
        double sum = 0;
        for (double value : october.values()) {
            sum += value;
        }
        return sum / october.size();
    }

    /**
     * After 1 February, if the last week was warm (at least 6 readings in the last 8 days, none cold),
     * treat the winter as over. This stops the system from expecting cold days that are no longer coming.
     */
    private static boolean looksFinished(SortedMap<LocalDate, Double> temperatures, Set<LocalDate> coldDates,
                                         int seasonYear, LocalDate today) {
        if (today.isBefore(LocalDate.of(seasonYear + 1, Month.FEBRUARY, 1))) {
            return false;
        }
        SortedMap<LocalDate, Double> recent = temperatures.subMap(today.minusDays(7), today.plusDays(1));
        if (recent.size() < 6) {
            return false;
        }
        for (LocalDate day : recent.keySet()) {
            if (coldDates.contains(day)) {
                return false;
            }
        }
        return true;
    }

    /** Usual middle of winter, in days after 1 November (about 8 January when there is no history). */
    private static long usualMiddleOffset(List<PastWinter> pastWinters) {
        double sum = 0;
        int count = 0;
        for (PastWinter winter : pastWinters) {
            if (winter.getFirstColdDay() != null) {
                LocalDate seasonStart = LocalDate.of(winter.getSeasonYear(), Month.NOVEMBER, 1);
                long firstOffset = ChronoUnit.DAYS.between(seasonStart, winter.getFirstColdDay());
                long lastOffset = ChronoUnit.DAYS.between(seasonStart, winter.getLastColdDay());
                sum += (firstOffset + lastOffset) / 2.0;
                count++;
            }
        }
        return count == 0 ? 68 : Math.round(sum / count);
    }

    private static double[] allColdDays(List<PastWinter> winters) {
        double[] days = new double[winters.size()];
        for (int i = 0; i < winters.size(); i++) {
            days[i] = winters.get(i).getColdDays();
        }
        return days;
    }
}
