package de.spahr.ausgaben.export;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Charakterisierungstests der reinen Budget-Perioden-Logik ({@link KmyDocument#monthOfKmyDate} und
 * {@link KmyDocument#budgetLevelResult}) – Grundlage des monatsgenauen Budget-Imports aus KMyMoney.
 */
public class BudgetLevelTest {

    // ---- monthOfKmyDate ----

    @Test
    public void month_validDate() {
        assertEquals(1, KmyDocument.monthOfKmyDate("2024-01-01"));
        assertEquals(3, KmyDocument.monthOfKmyDate("2024-03-15"));
        assertEquals(12, KmyDocument.monthOfKmyDate("2024-12-31"));
    }

    @Test
    public void month_invalidIsZero() {
        assertEquals(0, KmyDocument.monthOfKmyDate("2024-13-01")); // Monat 13 ungültig
        assertEquals(0, KmyDocument.monthOfKmyDate("garbage"));
        assertEquals(0, KmyDocument.monthOfKmyDate(null));
        assertEquals(0, KmyDocument.monthOfKmyDate(""));
    }

    // ---- budgetLevelResult ----

    @Test
    public void yearly_singlePeriod_isAnnualNoMonths() {
        long[] months = new long[12];
        months[0] = 120000; // Jahreswert steht (bei yearly) in einer Periode
        KmyDocument.BudgetLevelResult r = KmyDocument.budgetLevelResult("yearly", months, 120000, 1);
        assertEquals(120000, r.yearlyCents);
        assertNull("yearly hat keine Monatsaufteilung", r.monthlyCents);
    }

    @Test
    public void monthly_singlePeriod_timesTwelve() {
        long[] months = new long[12];
        months[0] = 10000; // ein Monatswert
        KmyDocument.BudgetLevelResult r = KmyDocument.budgetLevelResult("monthly", months, 10000, 1);
        assertEquals("Monatswert × 12 = Jahr", 120000, r.yearlyCents);
        assertNull("monthly wird gleichmäßig verteilt (kein Monatsarray)", r.monthlyCents);
    }

    @Test
    public void monthbymonth_keepsPerMonthAndSumsYear() {
        long[] months = new long[12];
        months[0] = 10000;  // Jan
        months[1] = 20000;  // Feb
        months[11] = 5000;  // Dez
        KmyDocument.BudgetLevelResult r =
                KmyDocument.budgetLevelResult("monthbymonth", months, 35000, 3);
        assertEquals("Jahr = Summe der Monate", 35000, r.yearlyCents);
        assertArrayEquals(months, r.monthlyCents);
    }

    @Test
    public void multiplePeriodsWithoutLevel_treatedAsPerMonth() {
        long[] months = new long[12];
        months[0] = 10000;
        months[6] = 30000;
        KmyDocument.BudgetLevelResult r = KmyDocument.budgetLevelResult("", months, 40000, 2);
        assertEquals(40000, r.yearlyCents);
        assertArrayEquals(months, r.monthlyCents);
    }
}
