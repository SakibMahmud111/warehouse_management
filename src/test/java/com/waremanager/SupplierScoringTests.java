//f5
package com.waremanager;

import static org.junit.jupiter.api.Assertions.*;
import com.waremanager.service.SupplierScoring;
import com.waremanager.service.SupplierScoring.Receipt;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplierScoringTests {
    @Test void perfectReceiptsScore100() {
        assertEquals(100.0, SupplierScoring.score(List.of(new Receipt(true, 100, 100, 0), new Receipt(true, 50, 50, 0))).score());
    }
    @Test void lateShortAndDefectiveLowersScore() {
        var r = SupplierScoring.score(List.of(new Receipt(false, 100, 80, 8)));
        assertEquals(0.0, r.onTimeRate());
        assertEquals(0.8, r.fillRate(), 1e-9);
        assertEquals(0.1, r.defectRate(), 1e-9);
        assertEquals(0.4 * 0 + 0.4 * 0.8 + 0.2 * 0.9, r.score() / 100, 0.001);
    }
    @Test void noReceiptsGivesZero() {
        assertEquals(0.0, SupplierScoring.score(List.of()).score());
    }
}