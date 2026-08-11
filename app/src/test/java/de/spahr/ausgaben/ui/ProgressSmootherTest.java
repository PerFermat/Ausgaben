package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Das Nachlaufen der Fortschrittsanzeige ({@link ProgressSmoother}): weich, monoton und ohne
 * Stillstand.
 */
public class ProgressSmootherTest {

    /** Läßt die Anzeige eine Weile takten und liefert den erreichten Wert. */
    private static int takte(ProgressSmoother s, long startMs, int anzahl) {
        int wert = 0;
        for (int i = 1; i <= anzahl; i++) {
            wert = s.tick(startMs + i * ProgressSmoother.TICK_MS);
        }
        return wert;
    }

    @Test
    public void holtDenGemeldetenWertZuegigEin() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(30, 0);
        // Ein Viertel des Abstands je Takt, mindestens 1: nach einer Sekunde ist der Wert erreicht.
        assertEquals(30, takte(s, 0, 1000 / (int) ProgressSmoother.TICK_MS));
    }

    @Test
    public void gehtNiemalsZurueck() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(60, 0);
        int oben = takte(s, 0, 40);
        assertEquals(60, oben);
        s.report(20, 2000);
        assertTrue(s.tick(2040) >= oben);
    }

    @Test
    public void kriechtOhneMeldungWeiter() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(50, 0);
        takte(s, 0, 40);                       // Ziel erreicht, ab hier meldet niemand mehr
        assertEquals(51, s.tick(40 * ProgressSmoother.TICK_MS + ProgressSmoother.CREEP_MS));
    }

    @Test
    public void kriechtHoechstensFuenfProzentVoraus() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(50, 0);
        takte(s, 0, 40);
        assertEquals(50 + ProgressSmoother.CREEP_MAX, s.tick(600_000));
    }

    @Test
    public void bleibtOhneFertigmeldungBei99() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(99, 0);
        takte(s, 0, 200);
        assertEquals(ProgressSmoother.CEILING, s.tick(600_000));
        s.finish();
        assertEquals(100, s.tick(600_040));
    }

    @Test
    public void nachDemZuruecksetzenBeginntEsWiederBei0() {
        ProgressSmoother s = new ProgressSmoother();
        s.report(80, 0);
        takte(s, 0, 100);
        s.reset();
        assertEquals(0, s.shown());
        assertEquals(0, s.tick(ProgressSmoother.TICK_MS));
    }
}
