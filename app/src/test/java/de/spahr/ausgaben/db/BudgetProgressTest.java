package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests der verlaufsbasierten Budget-Balkenfarbe ({@link BudgetProgress}). */
public class BudgetProgressTest {

    private static final double TOL = 0.05;

    // ---- expectedFraction ----

    @Test
    public void earlyConcentratedIsFullEarly() {
        long[] h = new long[31];
        h[0] = 1000;                 // alles am 1. Tag
        assertEquals(1.0, BudgetProgress.expectedFraction(h, 1), 1e-9);
        assertEquals(0.0, BudgetProgress.expectedFraction(h, 0), 1e-9);
    }

    @Test
    public void uniformIsProportional() {
        long[] h = new long[12];
        for (int i = 0; i < 12; i++) {
            h[i] = 100;              // gleichmäßig übers Jahr
        }
        assertEquals(0.5, BudgetProgress.expectedFraction(h, 6), 1e-9);
        assertEquals(0.25, BudgetProgress.expectedFraction(h, 3), 1e-9);
    }

    @Test
    public void lateConcentratedIsZeroEarly() {
        long[] h = new long[31];
        h[30] = 500;                 // alles am Monatsende
        assertEquals(0.0, BudgetProgress.expectedFraction(h, 5), 1e-9);
        assertEquals(1.0, BudgetProgress.expectedFraction(h, 31), 1e-9);
    }

    @Test
    public void noHistoryReturnsMinusOne() {
        assertEquals(-1, BudgetProgress.expectedFraction(new long[31], 10), 1e-9);
        assertEquals(-1, BudgetProgress.expectedFraction(null, 10), 1e-9);
    }

    // ---- onTrack (Ampel) ----

    @Test
    public void expenseGreenWhenNotBeyondExpected() {
        // Monatskarte: früh voll ausgeschöpft, Verlauf erwartet bereits alles → grün.
        assertTrue(BudgetProgress.onTrack(false, 1.0, 1.0, TOL));
        // Regelmäßig: zu früh zu viel → rot.
        assertFalse(BudgetProgress.onTrack(false, 1.0, 0.1, TOL));
        // Regelmäßig im Rahmen.
        assertTrue(BudgetProgress.onTrack(false, 0.4, 0.5, TOL));
        assertFalse(BudgetProgress.onTrack(false, 0.6, 0.5, TOL));
    }

    @Test
    public void incomeGreenWhenAtLeastExpected() {
        // Gehalt vor Zahltag: nichts erwartet, nichts da → grün.
        assertTrue(BudgetProgress.onTrack(true, 0.0, 0.0, TOL));
        // Nach Zahltag empfangen → grün.
        assertTrue(BudgetProgress.onTrack(true, 1.0, 1.0, TOL));
        // Nach Zahltag ausgeblieben → rot.
        assertFalse(BudgetProgress.onTrack(true, 0.0, 1.0, TOL));
    }
}
