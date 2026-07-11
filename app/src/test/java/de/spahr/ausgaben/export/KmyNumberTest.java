package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Charakterisierungstests für die reinen KMyMoney-Parser {@link KmyDocument#fractionToDouble} und
 * {@link KmyDocument#parseKmyDate} – Grundlage von Depot-Kursen und Budget-Beträgen.
 */
public class KmyNumberTest {

    @Test
    public void fraction_numeratorOverDenominator() {
        assertEquals(100.0, KmyDocument.fractionToDouble("100/1"), 1e-9);
        assertEquals(20.5, KmyDocument.fractionToDouble("2050/100"), 1e-9);
    }

    @Test
    public void fraction_repeatingIsApproximate() {
        assertEquals(1.0 / 3.0, KmyDocument.fractionToDouble("1/3"), 1e-9);
    }

    @Test
    public void fraction_plainNumberWithoutSlash() {
        assertEquals(5.0, KmyDocument.fractionToDouble("5"), 1e-9);
        assertEquals(3.14, KmyDocument.fractionToDouble("3.14"), 1e-9);
    }

    @Test
    public void fraction_invalidOrZeroDenominatorIsZero() {
        assertEquals(0.0, KmyDocument.fractionToDouble("1/0"), 1e-9);
        assertEquals(0.0, KmyDocument.fractionToDouble("abc"), 1e-9);
        assertEquals(0.0, KmyDocument.fractionToDouble(null), 1e-9);
        assertEquals(0.0, KmyDocument.fractionToDouble(""), 1e-9);
    }

    @Test
    public void date_validParsesAndOrders() {
        assertTrue(KmyDocument.parseKmyDate("2019-06-15") >= 0);
        // Spätere Datumsangabe ergibt einen größeren Millis-Wert (zeitzonenunabhängig).
        assertTrue(KmyDocument.parseKmyDate("2019-01-02") > KmyDocument.parseKmyDate("2019-01-01"));
    }

    @Test
    public void date_invalidIsMinusOne() {
        assertEquals(-1, KmyDocument.parseKmyDate("garbage"));
        assertEquals(-1, KmyDocument.parseKmyDate(null));
        assertEquals(-1, KmyDocument.parseKmyDate(""));
    }
}
