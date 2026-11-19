import static org.junit.jupiter.api.Assertions.*;
import com.waremanager.service.StockHealthClassifier;
import com.waremanager.service.StockHealthClassifier.Status;
import org.junit.jupiter.api.Test;

class StockHealthClassifierTests {
    @Test void noSaleInWindowIsDead() {
        assertEquals(Status.DEAD, StockHealthClassifier.classify(50, 0, 120, 10.0, 90, 120).status());
    }
    @Test void noSalesEverButStockIsDead() {
        assertEquals(Status.DEAD, StockHealthClassifier.classify(50, 0, null, 10.0, 90, 120).status());
    }
    @Test void highCoverIsOverstock() {      // 500 units, 14/week => 250 days
        assertEquals(Status.OVERSTOCK, StockHealthClassifier.classify(500, 14, 3, 10.0, 90, 120).status());
    }
    @Test void mediumCoverIsSlow() {         // 100 units, 7/week => 100 days (60 < 100 <= 120)
        assertEquals(Status.SLOW, StockHealthClassifier.classify(100, 7, 3, 10.0, 90, 120).status());
    }
    @Test void healthyStockNeedsNoAction() {
        assertEquals(Status.HEALTHY, StockHealthClassifier.classify(30, 70, 2, 10.0, 90, 120).status());
    }
    @Test void valueIsQuantityTimesCost() {
        assertEquals(500.0, StockHealthClassifier.classify(50, 70, 2, 10.0, 90, 120).valueTiedUp());
    }
    @Test void emptyShelfIsNotDead() {
        assertEquals(Status.HEALTHY, StockHealthClassifier.classify(0, 0, null, 10.0, 90, 120).status());
    }
}