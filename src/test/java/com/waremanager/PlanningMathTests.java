package com.waremanager;

import com.waremanager.service.DemandForecaster;
import com.waremanager.service.DemandModel;
import com.waremanager.service.DemoDataGenerator;
import com.waremanager.service.InventoryMath;
import com.waremanager.service.Statistics;
import com.waremanager.service.WeekForecast;
import com.waremanager.service.WeekSales;
import com.waremanager.service.WinterForecaster;
import com.waremanager.service.WinterOutlook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the plain-Java planning maths (no Spring, no database), with a fixed "today"
 * so the expected numbers never change.
 */
class PlanningMathTests {

    private static final LocalDate NOV_14_2026 = LocalDate.of(2026, 11, 14);

    // ---------------------------------------------------------------- Statistics

    @Test
    void normalDistributionValuesMatchTables() {
        assertEquals(0.5, Statistics.normalCdf(0), 1e-6);
        assertEquals(0.95, Statistics.normalCdf(1.6449), 1e-4);
        assertEquals(1.645, Statistics.normalInverse(0.95), 1e-3);
        assertEquals(2.326, Statistics.normalInverse(0.99), 1e-3);
        assertEquals(0.0, Statistics.normalInverse(0.5), 1e-6);
    }

    @Test
    void regressionFindsAPerfectLine() {
        Statistics.LineFit line = Statistics.fitLine(new double[]{1, 2, 3, 4}, new double[]{3, 5, 7, 9}); // y = 2x + 1
        assertEquals(2, line.getSlope(), 1e-9);
        assertEquals(1, line.getIntercept(), 1e-9);
        assertEquals(1, line.getRSquared(), 1e-9);
        assertEquals(11, line.predict(5), 1e-9);
    }

    // ---------------------------------------------------------------- F2 InventoryMath

    @Test
    void safetyStockFormula() {
        // z=1.645, d=10/day, σd=2/day, LT=4 days, σLT=1 day -> 1.645 * sqrt(4*4 + 100*1) = 17.7
        assertEquals(17.72, InventoryMath.safetyStock(1.645, 10, 2, 4, 1), 0.01);
        assertEquals(0, InventoryMath.safetyStock(-0.5, 10, 2, 4, 1), 1e-9); // never negative
    }

    @Test
    void criticalRatioBalancesProfitAndLeftoverLoss() {
        assertEquals(400.0 / 700.0, InventoryMath.criticalRatio(1250, 850, 550), 1e-9);
        assertEquals(0.99, InventoryMath.criticalRatio(100, 80, 80), 1e-9);  // leftovers lose nothing
        assertEquals(0.01, InventoryMath.criticalRatio(80, 100, 50), 1e-9);  // sold at a loss
    }

    // ---------------------------------------------------------------- F1 winter prediction

    @Test
    void shortWinterIsPredictedAfterAWarmOctober() {
        SortedMap<LocalDate, Double> temperatures = DemoDataGenerator.temperatures(NOV_14_2026);
        WinterOutlook outlook = WinterForecaster.predict(temperatures, NOV_14_2026, 20.0);

        assertEquals(2026, outlook.getSeasonYear());
        assertEquals(8, outlook.getPastWinters().size());
        assertEquals(27, outlook.getPastWinters().get(7).getColdDays());       // last winter
        assertEquals(64, outlook.getPastWinters().get(0).getColdDays());       // eight winters ago
        assertTrue(outlook.getMethod().startsWith("Linear regression"));
        assertTrue(outlook.getRSquared() > 0.8, "October temperature should explain winter length well");
        assertTrue(outlook.getPredictedDays() >= 18 && outlook.getPredictedDays() <= 30,
                "predicted " + outlook.getPredictedDays());
        assertTrue(outlook.getDifferenceFromNormal() < -15, "this winter should be much shorter than normal");
        assertEquals(0, outlook.getColdDaysSoFar());
        assertEquals(outlook.getPredictedDays(), outlook.getRemainingDays());
        assertTrue(outlook.getExpectedStart().isAfter(NOV_14_2026));
    }

    @Test
    void winterInProgressCountsDaysAlreadyPassed() {
        LocalDate jan5 = LocalDate.of(2027, 1, 5);
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(jan5), jan5, 20.0);
        assertEquals(2026, outlook.getSeasonYear());
        assertTrue(outlook.isInProgress());
        assertTrue(outlook.getColdDaysSoFar() > 0);
        assertEquals(outlook.getPredictedDays() - outlook.getColdDaysSoFar(), outlook.getRemainingDays());
    }

    @Test
    void finishedWinterNeedsNoMoreStock() {
        LocalDate feb20 = LocalDate.of(2027, 2, 20);
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(feb20), feb20, 20.0);
        assertTrue(outlook.isFinished());
        assertEquals(0, outlook.getRemainingDays());
        assertNull(outlook.getExpectedStart());
    }

    @Test
    void withoutThisOctoberTheAverageOfRecentWintersIsUsed() {
        LocalDate june = LocalDate.of(2027, 6, 15);
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(june), june, 20.0);
        assertTrue(outlook.getMethod().startsWith("Average of the last 3 winters"));
        assertFalse(outlook.isInProgress());
    }

    // ---------------------------------------------------------------- F1 demand model

    @Test
    void winterProductLearnsExtraSalesPerColdDay() {
        DemandModel blanket = modelFor("WM-BLK-01", NOV_14_2026);
        assertTrue(blanket.isWinterModel());
        assertEquals(9.0, blanket.getPerColdDay(), 1.0);   // the demo data adds 9 per cold day
        assertEquals(4.0, blanket.getBaseWeekly(), 1.5);
    }

    @Test
    void steadyProductUsesMovingAverageAndLearnsEidUplift() {
        DemandModel rice = modelFor("WM-RCE-04", NOV_14_2026);
        assertFalse(rice.isWinterModel());
        assertEquals(120, rice.getBaseWeekly(), 15);
        assertTrue(rice.getEventFactor() > 1.2, "Eid weeks should sell more");
        assertTrue(rice.getErrorStd() > 0);
    }

    @Test
    void forecastFollowsThePredictedWinter() {
        LocalDate today = NOV_14_2026;
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(today), today, 20.0);
        DemandModel blanket = modelFor("WM-BLK-01", today);
        List<WeekForecast> weeks = DemandForecaster.forecast(blanket, LocalDate.of(2026, 11, 9), 12, outlook, Set.of());
        double first = weeks.get(0).getQuantity();
        double peak = weeks.stream().mapToDouble(WeekForecast::getQuantity).max().orElse(0);
        assertTrue(peak > first * 5, "winter weeks should forecast far more blankets than November weeks");
        assertEquals(outlook.getRemainingDays(), weeks.stream().mapToInt(WeekForecast::getExpectedColdDays).sum());

        // Lead-time demand: 7 days of a steady 70/week forecast = 70
        List<WeekForecast> steady = List.of(new WeekForecast(LocalDate.of(2026, 11, 9), 0, false, 70),
                new WeekForecast(LocalDate.of(2026, 11, 16), 0, false, 70));
        assertEquals(70, DemandForecaster.demandOverDays(steady, LocalDate.of(2026, 11, 10), 7), 1e-9);
    }

    private static DemandModel modelFor(String sku, LocalDate today) {
        SortedMap<LocalDate, Double> temperatures = DemoDataGenerator.temperatures(today);
        WinterOutlook outlook = WinterForecaster.predict(temperatures, today, 20.0);
        DemoDataGenerator.DemoProduct product = DemoDataGenerator.products().stream()
                .filter(p -> p.sku.equals(sku)).findFirst().orElseThrow();
        List<WeekSales> sales = DemoDataGenerator.weeklySales(product, temperatures, 20.0, DemoDataGenerator.events(), today);
        return DemandForecaster.fit(sales, product.seasonal, outlook);
    }
}
