package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

/** Tests der Auffaltung geplanter Buchungen in Einzeltermine ({@link ScheduleProjection}). */
public class ScheduleProjectionTest {

    private static long ymd(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    private static long plusDays(long base, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(base);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    @Test
    public void weeklyExpandsForwardOnly() {
        long base = ymd(2026, 1, 15);            // occurrence 4 = wöchentlich
        long from = plusDays(base, -10);         // Untergrenze vor dem Basistermin – darf nichts davor liefern
        long to = plusDays(base, 30);
        List<Long> occ = ScheduleProjection.occurrences(base, 4, 1, 0, from, to, 24);
        // Nur vorwärts ab base: base, +7, +14, +21, +28 (kein base-7)
        assertEquals(5, occ.size());
        assertEquals(base, (long) occ.get(0));
        assertEquals(plusDays(base, 28), (long) occ.get(4));
        for (int i = 1; i < occ.size(); i++) {
            assertEquals(7L * 24 * 3600 * 1000, occ.get(i) - occ.get(i - 1));
        }
    }

    @Test
    public void monthlyStepsByMonthNoDrift() {
        long base = ymd(2026, 1, 31);            // occurrence 32 = monatlich, 31. → Monatsende
        long from = plusDays(base, -5);
        long to = ymd(2026, 5, 1);
        List<Long> occ = ScheduleProjection.occurrences(base, 32, 1, 0, from, to, 24);
        // Jan 31, Feb 28 (geklemmt), Mär 31, Apr 30 – aus dem Basisdatum berechnet (keine Drift).
        assertEquals(4, occ.size());
        assertEquals(ymd(2026, 1, 31), (long) occ.get(0));
        assertEquals(ymd(2026, 2, 28), (long) occ.get(1));
        assertEquals(ymd(2026, 3, 31), (long) occ.get(2));
        assertEquals(ymd(2026, 4, 30), (long) occ.get(3));
    }

    @Test
    public void yearlyOncePerYear() {
        long base = ymd(2026, 7, 15);            // occurrence 16384 = jährlich
        long from = plusDays(base, -30);
        long to = plusDays(base, 400);
        List<Long> occ = ScheduleProjection.occurrences(base, 16384, 1, 0, from, to, 24);
        assertEquals(2, occ.size());             // 2026 und 2027
        assertEquals(ymd(2026, 7, 15), (long) occ.get(0));
        assertEquals(ymd(2027, 7, 15), (long) occ.get(1));
    }

    @Test
    public void endDateCapsProjection() {
        long base = ymd(2026, 1, 15);
        long from = plusDays(base, -5);
        long to = plusDays(base, 400);
        long end = ymd(2026, 3, 20);             // endet Mitte März
        List<Long> occ = ScheduleProjection.occurrences(base, 32, 1, end, from, to, 24);
        assertEquals(3, occ.size());             // Jan, Feb, Mär (Apr liegt nach dem Ende)
        assertTrue(occ.get(occ.size() - 1) <= end);
    }

    @Test
    public void noDateIsEmpty() {
        assertTrue(ScheduleProjection.occurrences(0, 32, 1, 0, 0, Long.MAX_VALUE, 24).isEmpty());
    }

    @Test
    public void onceOnlyWhenInWindow() {
        long base = ymd(2026, 6, 1);
        assertEquals(1, ScheduleProjection.occurrences(base, 1, 1, 0,
                plusDays(base, -5), plusDays(base, 5), 24).size());
        assertTrue(ScheduleProjection.occurrences(base, 1, 1, 0,
                plusDays(base, 10), plusDays(base, 20), 24).isEmpty());
    }
}
