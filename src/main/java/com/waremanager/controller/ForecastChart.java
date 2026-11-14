package com.waremanager.controller;

import com.waremanager.model.DemandForecast;
import com.waremanager.service.ProductForecast;
import com.waremanager.service.WeekSales;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * View helper for the F1 page: turns sales, model values and forecasts into SVG coordinates.
 * It only does drawing maths; all forecasting happens in the service layer.
 */
public class ForecastChart {

    public static final int WIDTH = 760;
    public static final int HEIGHT = 240;
    private static final int LEFT = 48;
    private static final int RIGHT = 12;
    private static final int TOP = 12;
    private static final int BOTTOM = 28;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    private final String actualPoints;
    private final String modelPoints;
    private final String forecastPoints;
    private final double todayX;
    private final List<Tick> valueTicks = new ArrayList<>();
    private final List<Tick> weekTicks = new ArrayList<>();
    private final boolean empty;

    /** A label on an axis: its position in the SVG and its text. */
    public static class Tick {
        private final double position;
        private final String label;

        Tick(double position, String label) {
            this.position = position;
            this.label = label;
        }

        public double getPosition() { return position; }
        public String getLabel() { return label; }
    }

    public ForecastChart(ProductForecast data) {
        List<WeekSales> history = data.getLastYear();
        List<DemandForecast> future = data.getForecasts();
        Map<LocalDate, Double> fitted = data.getModel().getFitted();

        List<LocalDate> weeks = new ArrayList<>();
        double max = 1;
        for (WeekSales week : history) {
            weeks.add(week.getWeekStart());
            max = Math.max(max, week.getQuantity());
            max = Math.max(max, fitted.getOrDefault(week.getWeekStart(), 0.0));
        }
        for (DemandForecast forecast : future) {
            weeks.add(forecast.getWeekStart());
            max = Math.max(max, forecast.getQuantity());
        }
        empty = weeks.size() < 2;
        max = niceMaximum(max * 1.1);

        StringBuilder actual = new StringBuilder();
        StringBuilder model = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            WeekSales week = history.get(i);
            actual.append(point(i, weeks.size(), week.getQuantity(), max));
            model.append(point(i, weeks.size(), fitted.getOrDefault(week.getWeekStart(), 0.0), max));
        }
        StringBuilder forecast = new StringBuilder();
        if (!history.isEmpty()) {
            // start the forecast line where the model line ends, so the two connect
            int last = history.size() - 1;
            forecast.append(point(last, weeks.size(), fitted.getOrDefault(history.get(last).getWeekStart(), 0.0), max));
        }
        for (int i = 0; i < future.size(); i++) {
            forecast.append(point(history.size() + i, weeks.size(), future.get(i).getQuantity(), max));
        }
        actualPoints = actual.toString().trim();
        modelPoints = model.toString().trim();
        forecastPoints = forecast.toString().trim();
        todayX = x(Math.max(0, history.size() - 0.5), weeks.size());

        for (int i = 0; i <= 4; i++) {
            double value = max * i / 4;
            valueTicks.add(new Tick(y(value, max), String.valueOf(Math.round(value))));
        }
        for (int i = 0; i < weeks.size(); i += 8) {
            weekTicks.add(new Tick(x(i, weeks.size()), weeks.get(i).format(MONTH)));
        }
    }

    private static String point(double index, int count, double value, double max) {
        return String.format(Locale.ROOT, "%.1f,%.1f ", x(index, count), y(value, max));
    }

    private static double x(double index, int count) {
        double width = WIDTH - LEFT - RIGHT;
        return count < 2 ? LEFT : LEFT + index * width / (count - 1);
    }

    private static double y(double value, double max) {
        double height = HEIGHT - TOP - BOTTOM;
        return TOP + height * (1 - value / max);
    }

    /** Rounds the axis maximum up to 1, 2 or 5 × a power of ten, so the labels look tidy. */
    private static double niceMaximum(double value) {
        double power = Math.pow(10, Math.floor(Math.log10(value)));
        for (double step : new double[]{1, 2, 5, 10}) {
            if (step * power >= value) {
                return step * power;
            }
        }
        return 10 * power;
    }

    public String getActualPoints() { return actualPoints; }
    public String getModelPoints() { return modelPoints; }
    public String getForecastPoints() { return forecastPoints; }
    public double getTodayX() { return todayX; }
    public List<Tick> getValueTicks() { return valueTicks; }
    public List<Tick> getWeekTicks() { return weekTicks; }
    public boolean isEmpty() { return empty; }
    public int getWidth() { return WIDTH; }
    public int getHeight() { return HEIGHT; }
    public int getLeft() { return LEFT; }
    public int getRight() { return WIDTH - RIGHT; }
    public int getBottom() { return HEIGHT - BOTTOM; }
}
