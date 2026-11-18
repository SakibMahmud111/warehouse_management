package com.waremanager.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds realistic sample data so F1 and F2 can be demonstrated right after the first start.
 *
 * The story behind the numbers (Dhaka-like climate):
 *  - Eight winters ago a winter had about 64 cold days (about 2 months).
 *  - Winters have been getting shorter; last winter had only 27 cold days.
 *  - This October was unusually warm (28.2 °C), so this winter is expected to be short again (about 3 weeks).
 * Winter products (blanket, jacket, shawl) sell much more on cold days; groceries sell steadily,
 * with a jump in Eid shopping weeks.
 * Each product has a supplier lead time and how much it varies, which F2 uses.
 *
 * Everything is calculated relative to "today", so the demo works on any date.
 * The numbers are fixed by a seed, so every run produces the same data for the same date.
 * Plain Java with no database access: {@link DemoDataService} saves the results.
 */
public final class DemoDataGenerator {

    /** The demo data is built for the default cold-day threshold of 20 °C. */
    public static final double COLD_THRESHOLD = 20.0;
    /** Cold days in each of the last 8 winters, oldest first. */
    private static final int[] PAST_WINTER_DAYS = {64, 59, 61, 53, 48, 42, 35, 27};
    /** Length used to generate the current winter's readings (only matters once it has started). */
    private static final int CURRENT_WINTER_DAYS = 21;
    /** This October was unusually warm. */
    private static final double CURRENT_OCTOBER_AVERAGE = 28.2;

    private DemoDataGenerator() {
        // only static methods
    }

    // ---------------------------------------------------------------- products

    /** A sample product and how its demand behaves. */
    public static final class DemoProduct {
        public final String sku;
        public final String name;
        public final String category;
        public final boolean seasonal;
        public final double unitCost;
        public final double sellingPrice;
        public final double clearancePrice;
        public final int stockOnHand;
        public final double leadTimeDays;
        public final double leadTimeStdDays;
        final double baseWeekly;
        final double extraPerColdDay;
        final double eventUplift;

        DemoProduct(String sku, String name, String category, boolean seasonal, double unitCost, double sellingPrice,
                    double clearancePrice, int stockOnHand, double leadTimeDays, double leadTimeStdDays,
                    double baseWeekly, double extraPerColdDay, double eventUplift) {
            this.sku = sku;
            this.name = name;
            this.category = category;
            this.seasonal = seasonal;
            this.unitCost = unitCost;
            this.sellingPrice = sellingPrice;
            this.clearancePrice = clearancePrice;
            this.stockOnHand = stockOnHand;
            this.leadTimeDays = leadTimeDays;
            this.leadTimeStdDays = leadTimeStdDays;
            this.baseWeekly = baseWeekly;
            this.extraPerColdDay = extraPerColdDay;
            this.eventUplift = eventUplift;
        }
    }

    public static List<DemoProduct> products() {
        List<DemoProduct> list = new ArrayList<>();
        //                    sku          name                       category        winter cost  price  clear  stock LT  LTsd base extra/cold eid
        list.add(new DemoProduct("WM-BLK-01", "Fleece Blanket (Double)", "Winter wear", true, 850, 1250, 550, 40, 12, 3, 4, 9, 1.0));
        list.add(new DemoProduct("WM-JKT-02", "Men's Winter Jacket", "Winter wear", true, 1400, 2200, 900, 15, 15, 4, 1, 4, 1.0));
        list.add(new DemoProduct("WM-SHL-03", "Woolen Shawl", "Winter wear", true, 450, 750, 300, 25, 10, 2, 2, 6, 1.0));
        list.add(new DemoProduct("WM-RCE-04", "Miniket Rice 25 kg", "Grocery", false, 1650, 1850, 1650, 260, 5, 1, 120, 0, 1.35));
        list.add(new DemoProduct("WM-OIL-05", "Soybean Oil 5 L", "Grocery", false, 820, 920, 820, 95, 7, 2, 85, 0, 1.5));
        list.add(new DemoProduct("WM-SOP-06", "Bath Soap (Pack of 4)", "Personal care", false, 140, 190, 140, 200, 6, 1.5, 210, 0, 1.2));
        return list;
    }

    // ---------------------------------------------------------------- events

    /** A holiday or promotion period. */
    public static final class DemoEvent {
        public final String name;
        public final LocalDate start;
        public final LocalDate end;

        DemoEvent(String name, LocalDate start, LocalDate end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    /** Eid shopping weeks (approximate Bangladesh dates) and Pohela Boishakh, 2022-2028. */
    public static List<DemoEvent> events() {
        String[] eidUlFitr = {"2022-05-03", "2023-04-22", "2024-04-11", "2025-03-31", "2026-03-21", "2027-03-10", "2028-02-27"};
        String[] eidUlAdha = {"2022-07-10", "2023-06-29", "2024-06-17", "2025-06-07", "2026-05-27", "2027-05-17", "2028-05-05"};
        List<DemoEvent> list = new ArrayList<>();
        for (String date : eidUlFitr) {
            LocalDate eid = LocalDate.parse(date);
            list.add(new DemoEvent("Eid ul-Fitr shopping " + eid.getYear(), eid.minusDays(7), eid));
        }
        for (String date : eidUlAdha) {
            LocalDate eid = LocalDate.parse(date);
            list.add(new DemoEvent("Eid ul-Adha shopping " + eid.getYear(), eid.minusDays(7), eid));
        }
        for (int year = 2022; year <= 2028; year++) {
            list.add(new DemoEvent("Pohela Boishakh " + year, LocalDate.of(year, Month.APRIL, 8), LocalDate.of(year, Month.APRIL, 14)));
        }
        return list;
    }

    /** True if any event overlaps the week starting on {@code monday}. */
    public static boolean isEventWeek(LocalDate monday, List<DemoEvent> events) {
        LocalDate sunday = monday.plusDays(6);
        for (DemoEvent event : events) {
            if (!event.start.isAfter(sunday) && !event.end.isBefore(monday)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- temperatures

    /** Daily average temperatures from 1 January eight years ago until yesterday. */
    public static SortedMap<LocalDate, Double> temperatures(LocalDate today) {
        int currentSeason = WinterForecaster.seasonYearFor(today);
        LocalDate first = LocalDate.of(currentSeason - PAST_WINTER_DAYS.length, Month.JANUARY, 1);
        SortedMap<LocalDate, Double> result = new TreeMap<>();
        for (LocalDate day = first; day.isBefore(today); day = day.plusDays(1)) {
            result.put(day, Math.round(temperatureOn(day, currentSeason) * 10) / 10.0);
        }
        return result;
    }

    private static double temperatureOn(LocalDate day, int currentSeason) {
        // Which winter does this day belong to? Nov-Dec: this year's; Jan-Mar: last year's.
        int season = day.getMonthValue() <= 3 ? day.getYear() - 1 : day.getYear();
        boolean inWinterWindow = day.getMonthValue() >= 11 || day.getMonthValue() <= 3;
        SplittableRandom random = new SplittableRandom(day.toEpochDay());

        if (inWinterWindow) {
            int length = winterLength(season, currentSeason);
            LocalDate middle = LocalDate.of(season + 1, Month.JANUARY, 8).plusDays(new SplittableRandom(season).nextInt(7) - 3);
            LocalDate coldStart = middle.minusDays(length / 2);
            LocalDate coldEnd = coldStart.plusDays(length - 1L);
            if (!day.isBefore(coldStart) && !day.isAfter(coldEnd)) {
                return 17.6 + (random.nextDouble() * 2.4 - 1.2);       // cold day: 16.4 - 18.8 °C
            }
        }
        if (day.getMonth() == Month.OCTOBER) {
            return octoberAverage(day.getYear(), currentSeason) + octoberNoise(day);
        }
        // Normal smooth yearly curve (coolest in mid-January, hottest in mid-July), never cold outside winter.
        double curve = 26.0 + 6.0 * Math.cos(2 * Math.PI * (day.getDayOfYear() - 196) / 365.25);
        return Math.max(20.6, curve + (random.nextDouble() * 2 - 1));
    }

    private static int winterLength(int season, int currentSeason) {
        if (season >= currentSeason) {
            return CURRENT_WINTER_DAYS;
        }
        int index = PAST_WINTER_DAYS.length - (currentSeason - season);
        return index >= 0 ? PAST_WINTER_DAYS[index] : 66;
    }

    /** Warmer Octobers came before shorter winters (about 0.045 °C warmer per day shorter). */
    private static double octoberAverage(int year, int currentSeason) {
        if (year >= currentSeason) {
            return CURRENT_OCTOBER_AVERAGE;
        }
        double noise = new SplittableRandom(year * 101L).nextDouble() * 0.5 - 0.25;
        return 26.3 + (64 - winterLength(year, currentSeason)) * 0.045 + noise;
    }

    /** Day-to-day noise for October, adjusted so the month's average is exactly the planned average. */
    private static double octoberNoise(LocalDate day) {
        double[] noise = new double[31];
        SplittableRandom random = new SplittableRandom(day.getYear() * 13L + 5);
        double sum = 0;
        for (int i = 0; i < 31; i++) {
            noise[i] = random.nextDouble() * 2 - 1;
            sum += noise[i];
        }
        return noise[day.getDayOfMonth() - 1] - sum / 31;
    }

    // ---------------------------------------------------------------- sales

    /** Weekly sales for the last 156 complete weeks (oldest first). */
    public static List<WeekSales> weeklySales(DemoProduct product, SortedMap<LocalDate, Double> temperatures,
                                              double coldThreshold, List<DemoEvent> events, LocalDate today) {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Random random = new Random(product.sku.hashCode());
        List<WeekSales> result = new ArrayList<>();
        for (int i = DemandForecaster.HISTORY_WEEKS; i >= 1; i--) {
            LocalDate monday = thisMonday.minusWeeks(i);
            int coldDays = 0;
            for (int d = 0; d < 7; d++) {
                Double temperature = temperatures.get(monday.plusDays(d));
                if (temperature != null && temperature <= coldThreshold) {
                    coldDays++;
                }
            }
            boolean event = isEventWeek(monday, events);
            double expected = product.baseWeekly + product.extraPerColdDay * coldDays;
            if (event) {
                expected *= product.eventUplift;
            }
            double noisy = expected * (1 + 0.10 * random.nextGaussian()) + random.nextGaussian();
            result.add(new WeekSales(monday, Math.max(0, Math.round(noisy)), event));
        }
        return result;
    }
}
