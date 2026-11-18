package com.waremanager.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * F1: learns a weekly sales model for one product and makes weekly forecasts.
 *
 *  - Winter products: linear regression "weekly sales = base + perColdDay × cold days in the week",
 *    learned from up to 3 years of sales and the temperature records (trained by {@link ModelTrainer}:
 *    Python + scikit-learn, or the Java backup).
 *  - Other products (or not enough winter data): 12-week moving average of normal weeks.
 *  - Holidays/promotions: the average uplift seen in past event weeks is applied to future event weeks.
 *
 * Plain Java with no database access: {@link ForecastService} loads the data and calls this class.
 */
public final class DemandForecaster {

    public static final int HISTORY_WEEKS = 156;
    public static final int MOVING_AVERAGE_WEEKS = 12;
    public static final int FORECAST_WEEKS = 12;
    private static final int MIN_WEEKS_FOR_WINTER_MODEL = 26;
    private static final int MIN_COLD_WEEKS_FOR_WINTER_MODEL = 4;
    private static final int ERROR_WEEKS = 52;

    private DemandForecaster() {
        // only static methods
    }

    /** Same as below, using the Java version of the model (for unit tests). */
    public static DemandModel fit(List<WeekSales> history, boolean winterProduct, WinterOutlook outlook) {
        return fit(history, winterProduct, outlook, JavaModelTrainer.INSTANCE);
    }

    /** Learns the model from {@code history} (oldest week first). */
    public static DemandModel fit(List<WeekSales> history, boolean winterProduct, WinterOutlook outlook,
                                  ModelTrainer models) {
        if (history.isEmpty()) {
            return new DemandModel("No sales history yet, so the forecast is 0", false,
                    0, 0, 1, 0, 0, 0, 0, new TreeMap<>());
        }

        List<WeekSales> window = history.subList(Math.max(0, history.size() - HISTORY_WEEKS), history.size());
        List<WeekSales> normalWeeks = new ArrayList<>();
        for (WeekSales week : window) {
            if (!week.isEventWeek()) {
                normalWeeks.add(week);
            }
        }

        double base = 0;
        double perColdDay = 0;
        double rSquared = 0;
        boolean winterModel = false;
        String method = null;

        // ----- Winter products: learn how much each cold day adds to weekly sales -----
        if (winterProduct && normalWeeks.size() >= MIN_WEEKS_FOR_WINTER_MODEL) {
            double[] coldDays = new double[normalWeeks.size()];
            double[] sales = new double[normalWeeks.size()];
            int weeksWithCold = 0;
            for (int i = 0; i < normalWeeks.size(); i++) {
                coldDays[i] = outlook.observedColdDaysInWeek(normalWeeks.get(i).getWeekStart());
                sales[i] = normalWeeks.get(i).getQuantity();
                if (coldDays[i] > 0) {
                    weeksWithCold++;
                }
            }
            if (weeksWithCold >= MIN_COLD_WEEKS_FOR_WINTER_MODEL) {
                Statistics.LineFit line = models.fitLine(coldDays, sales);
                if (line.getSlope() > 0) {
                    base = Math.max(0, line.getIntercept());
                    perColdDay = line.getSlope();
                    rSquared = line.getRSquared();
                    winterModel = true;
                    method = String.format("Winter regression: weekly sales = %.1f + %.1f × cold days (R² = %.2f, %d weeks)",
                            base, perColdDay, rSquared, normalWeeks.size());
                }
            }
        }

        // ----- Everything else: moving average (also the fallback for winter products) -----
        if (!winterModel) {
            List<WeekSales> source = normalWeeks.isEmpty() ? window : normalWeeks;
            List<WeekSales> recent = source.subList(Math.max(0, source.size() - MOVING_AVERAGE_WEEKS), source.size());
            double[] values = new double[recent.size()];
            for (int i = 0; i < recent.size(); i++) {
                values[i] = recent.get(i).getQuantity();
            }
            base = Statistics.mean(values);
            method = winterProduct
                    ? recent.size() + "-week moving average (not enough winter sales yet for the winter model)"
                    : recent.size() + "-week moving average";
        }

        // ----- Holiday / promotion uplift learned from past event weeks -----
        double ratioSum = 0;
        int ratioCount = 0;
        for (WeekSales week : window) {
            if (week.isEventWeek()) {
                int cold = winterModel ? outlook.observedColdDaysInWeek(week.getWeekStart()) : 0;
                double expected = base + perColdDay * cold;
                if (expected > 0.5) {
                    ratioSum += week.getQuantity() / expected;
                    ratioCount++;
                }
            }
        }
        double eventFactor = ratioCount >= 2 ? Math.min(3.0, Math.max(0.5, ratioSum / ratioCount)) : 1.0;

        // ----- What the model says each past week should have sold, and how wrong it was -----
        Map<LocalDate, Double> fitted = new TreeMap<>();
        for (WeekSales week : window) {
            int cold = winterModel ? outlook.observedColdDaysInWeek(week.getWeekStart()) : 0;
            double value = base + perColdDay * cold;
            if (week.isEventWeek()) {
                value *= eventFactor;
            }
            fitted.put(week.getWeekStart(), value);
        }
        List<WeekSales> lastYear = window.subList(Math.max(0, window.size() - ERROR_WEEKS), window.size());
        double[] errors = new double[lastYear.size()];
        double absoluteSum = 0;
        for (int i = 0; i < lastYear.size(); i++) {
            errors[i] = lastYear.get(i).getQuantity() - fitted.get(lastYear.get(i).getWeekStart());
            absoluteSum += Math.abs(errors[i]);
        }
        double errorStd = Statistics.standardDeviation(errors);
        double meanAbsoluteError = lastYear.isEmpty() ? 0 : absoluteSum / lastYear.size();

        return new DemandModel(method, winterModel, base, perColdDay, eventFactor, rSquared,
                errorStd, meanAbsoluteError, window.size(), fitted);
    }

    /** Forecasts {@code weeks} weeks starting on {@code firstMonday}. */
    public static List<WeekForecast> forecast(DemandModel model, LocalDate firstMonday, int weeks,
                                              WinterOutlook outlook, Set<LocalDate> eventMondays) {
        List<WeekForecast> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            LocalDate monday = firstMonday.plusWeeks(i);
            int cold = model.isWinterModel() ? outlook.expectedColdDaysInWeek(monday) : 0;
            boolean event = eventMondays.contains(monday);
            result.add(new WeekForecast(monday, cold, event, model.predict(cold, event)));
        }
        return result;
    }

    /**
     * Total forecast demand for {@code days} days starting on {@code from}
     * (each day gets 1/7 of its week's forecast). Used for lead-time demand in F2 and F3.
     */
    public static double demandOverDays(List<WeekForecast> forecasts, LocalDate from, int days) {
        if (forecasts.isEmpty() || days <= 0) {
            return 0;
        }
        LocalDate firstMonday = forecasts.get(0).getWeekStart();
        double total = 0;
        for (int i = 0; i < days; i++) {
            LocalDate day = from.plusDays(i);
            long weekIndex = ChronoUnit.DAYS.between(firstMonday, day) / 7;
            weekIndex = Math.max(0, Math.min(forecasts.size() - 1, weekIndex));
            total += forecasts.get((int) weekIndex).getQuantity() / 7.0;
        }
        return total;
    }
}
