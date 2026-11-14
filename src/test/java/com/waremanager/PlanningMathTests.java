package com.waremanager;

import com.waremanager.service.AnomalyService;
import com.waremanager.service.DemandForecaster;
import com.waremanager.service.DemandModel;
import com.waremanager.service.DemoDataGenerator;
import com.waremanager.service.InventoryMath;
import com.waremanager.service.LeadTimeEstimate;
import com.waremanager.service.LeadTimePredictor;
import com.waremanager.service.SeasonPlan;
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

    // ---------------------------------------------------------------- InventoryMath

    @Test
    void eoqTextbookExample() {
        // D = 1000/yr, S = 50, H = 2  ->  sqrt(2 * 1000 * 50 / 2) = sqrt(50000) = 223.6
        assertEquals(223.6, InventoryMath.eoq(1000, 50, 2), 0.1);
        assertEquals(224, InventoryMath.roundUpToMinimum(223.6, 100));
        assertEquals(250, InventoryMath.roundUpToMinimum(223.6, 250));   // supplier minimum wins
        assertEquals(0, InventoryMath.roundUpToMinimum(0, 50));          // nothing needed, nothing ordered
        // At EOQ, ordering cost = holding cost
        double q = InventoryMath.eoq(1000, 50, 2);
        assertEquals(447.2, InventoryMath.annualCost(1000, 50, 2, q), 0.1);
        assertTrue(InventoryMath.annualCost(1000, 50, 2, 100) > InventoryMath.annualCost(1000, 50, 2, q));
    }

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

    @Test
    void overstockingLosesMoney() {
        InventoryMath.SeasonOutcome right = InventoryMath.expectedOutcome(110, 100, 10, 1250, 850, 550);
        InventoryMath.SeasonOutcome tooMuch = InventoryMath.expectedOutcome(300, 100, 10, 1250, 850, 550);
        assertTrue(right.getExpectedProfit() > tooMuch.getExpectedProfit());
        assertEquals(200, tooMuch.getExpectedLeftover(), 1);
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

    // ---------------------------------------------------------------- F4 winter plan

    @Test
    void winterPlanBeatsStockingForTwoMonths() {
        LocalDate today = NOV_14_2026;
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(today), today, 20.0);
        DemandModel blanket = modelFor("WM-BLK-01", today);
        SeasonPlan plan = SeasonPlan.create(1250, 850, 550, 40, 50, 12, blanket, outlook, today, 60);

        assertTrue(plan.getOrderQuantity() > 0);
        assertTrue(plan.getTargetStock() < plan.getDefaultStock(), "a short winter needs less than 60 days of stock");
        assertTrue(plan.getUsualPlan().getExpectedLeftover() > 5 * plan.getRecommended().getExpectedLeftover());
        assertTrue(plan.getExtraProfit() > 0);
        assertTrue(plan.getOrderByDate().isBefore(outlook.getExpectedStart()));
    }

    @Test
    void noWinterStockIsBoughtAfterWinterEnds() {
        LocalDate feb20 = LocalDate.of(2027, 2, 20);
        WinterOutlook outlook = WinterForecaster.predict(DemoDataGenerator.temperatures(feb20), feb20, 20.0);
        SeasonPlan plan = SeasonPlan.create(1250, 850, 550, 40, 50, 12, modelFor("WM-BLK-01", feb20), outlook, feb20, 60);
        assertEquals(0, plan.getOrderQuantity());
        assertNull(plan.getOrderByDate());
    }

    // ---------------------------------------------------------------- F6 lead time

    @Test
    void leadTimeUsesManualValueUntilThreeDeliveries() {
        LeadTimeEstimate estimate = LeadTimePredictor.estimate(List.of(9, 11), 7, 2);
        assertFalse(estimate.isFromDeliveries());
        assertEquals(7, estimate.getPredictedDays(), 1e-9);
        assertEquals(2, estimate.getStdDays(), 1e-9);
    }

    @Test
    void leadTimeFollowsASupplierThatIsGettingSlower() {
        // 10 days for a long time, then 16, 16, 16: the prediction moves well above the plain average
        LeadTimeEstimate estimate = LeadTimePredictor.estimate(List.of(10, 10, 10, 10, 16, 16, 16), 7, 2);
        assertTrue(estimate.isFromDeliveries());
        double plainAverage = (10 * 4 + 16 * 3) / 7.0;
        assertTrue(estimate.getPredictedDays() > plainAverage, "recent deliveries should count more");
        assertTrue(estimate.getTrendPerDelivery() > 0);
        assertTrue(estimate.getLowDays() >= 1);
        // steady supplier: prediction equals the constant lead time
        assertEquals(5, LeadTimePredictor.estimate(List.of(5, 5, 5, 5), 9, 3).getPredictedDays(), 1e-9);
    }

    // ---------------------------------------------------------------- F7 stockout risk

    @Test
    void stockoutProbabilityFollowsTheBellCurve() {
        assertEquals(0.5, InventoryMath.stockoutProbability(100, 100, 20), 1e-6);       // stock = expected demand
        assertEquals(0.05, InventoryMath.stockoutProbability(132.9, 100, 20), 1e-3);     // 1.645 sd of buffer
        assertTrue(InventoryMath.stockoutProbability(0, 50, 10) > 0.99);                // nothing on hand
        assertEquals(0, InventoryMath.stockoutProbability(10, 0, 0), 1e-9);             // no demand, no risk
    }

    // ---------------------------------------------------------------- F20 anomaly rule

    @Test
    void anomalyNeedsBothPercentAndUnits() {
        assertTrue(AnomalyService.isAnomaly(444, 239, 50));      // +86%, 205 units
        assertTrue(AnomalyService.isAnomaly(29, 81, 50));        // -64%, 52 units
        assertFalse(AnomalyService.isAnomaly(260, 239, 50));     // +9%: normal noise
        assertFalse(AnomalyService.isAnomaly(4, 2, 50));         // +100% but only 2 units
        assertEquals(-64.2, AnomalyService.deviationPercent(29, 81), 0.1);
    }

    @Test
    void demoDataHidesExactlyTwoUnusualWeeks() {
        LocalDate today = NOV_14_2026;
        SortedMap<LocalDate, Double> temperatures = DemoDataGenerator.temperatures(today);
        WinterOutlook outlook = WinterForecaster.predict(temperatures, today, 20.0);
        int found = 0;
        for (DemoDataGenerator.DemoProduct product : DemoDataGenerator.products()) {
            List<WeekSales> sales = DemoDataGenerator.weeklySales(product, temperatures, 20.0, DemoDataGenerator.events(), today);
            DemandModel model = DemandForecaster.fit(sales, product.seasonal, outlook);
            for (WeekSales week : sales.subList(sales.size() - 12, sales.size())) {
                if (AnomalyService.isAnomaly(week.getQuantity(), model.getFitted().get(week.getWeekStart()), 50)) {
                    found++;
                }
            }
        }
        assertEquals(2, found);
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
