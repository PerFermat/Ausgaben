package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Die Dauer der Rückfahrt an den Listenanfang ({@link ScrollToTop}): mit der Strecke wachsend,
 * nach oben gedeckelt.
 */
public class ScrollToTopTest {

    /** Eine Bildschirmhöhe in Pixeln – der Bezug, an dem die Dauer hängt. */
    private static final int BILDSCHIRM = 2000;

    @Test
    public void eineBildschirmhoeheDauertDenGrundwert() {
        assertEquals(ScrollToTop.MS_JE_BILDSCHIRM, ScrollToTop.dauerMs(BILDSCHIRM, BILDSCHIRM));
    }

    @Test
    public void wenigGescrolltHeisstKuerzer() {
        int kurz = ScrollToTop.dauerMs(BILDSCHIRM / 2, BILDSCHIRM);
        int lang = ScrollToTop.dauerMs(BILDSCHIRM * 2, BILDSCHIRM);
        assertTrue(kurz < lang);
        assertEquals(ScrollToTop.MS_JE_BILDSCHIRM / 2, kurz);
    }

    @Test
    public void eineLangeListeDauertHoechstensEineSekunde() {
        assertEquals(ScrollToTop.MAX_MS, ScrollToTop.dauerMs(BILDSCHIRM * 500, BILDSCHIRM));
    }

    @Test
    public void einWinzigesStueckIstNochEineBewegung() {
        assertEquals(ScrollToTop.MIN_MS, ScrollToTop.dauerMs(3, BILDSCHIRM));
    }

    /** Ohne gemessene Höhe (Liste noch nicht gelegt) bleibt es bei der Untergrenze. */
    @Test
    public void ohneMasseKeineRechnung() {
        assertEquals(ScrollToTop.MIN_MS, ScrollToTop.dauerMs(0, BILDSCHIRM));
        assertEquals(ScrollToTop.MIN_MS, ScrollToTop.dauerMs(BILDSCHIRM, 0));
    }
}
