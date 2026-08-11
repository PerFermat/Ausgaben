package de.spahr.ausgaben.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Die Rang-Rechnung hinter den Betrags-Schiebereglern ({@link Quantile}).
 */
public class QuantileTest {

    /** Zehn Werte 100…1000 (Cent), aufsteigend. */
    private static final long[] ZEHN = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

    @Test
    public void nullProzentIstDerKleinsteWert() {
        assertEquals(100, Quantile.valueAt(ZEHN, 0));
    }

    @Test
    public void hundertProzentIstDerGroesste() {
        assertEquals(1000, Quantile.valueAt(ZEHN, 100));
    }

    /** Der rechte Daumen auf 90 % schneidet das größte Zehntel ab. */
    @Test
    public void neunzigProzentSchneidetDasGroessteZehntelAb() {
        assertEquals(900, Quantile.valueAt(ZEHN, 90));
    }

    /** Ausgaben stehen links (negativ), Einnahmen rechts – 0 % ist die größte Ausgabe. */
    @Test
    public void ausgabenLiegenLinksEinnahmenRechts() {
        long[] gemischt = {-5000, -1200, -300, 250, 9000};
        assertEquals(-5000, Quantile.valueAt(gemischt, 0));
        assertEquals(9000, Quantile.valueAt(gemischt, 100));
    }

    @Test
    public void gleicheBetraegeLiegenAufDemselbenRang() {
        long[] viermalGleich = {499, 499, 499, 499, 2000};
        assertEquals(499, Quantile.valueAt(viermalGleich, 50));
        // Der Rang eines wiederholten Werts ist sein letzter – der Daumen steht am Ende des Blocks.
        assertEquals(75f, Quantile.percentOf(viermalGleich, 499), 0.01f);
    }

    @Test
    public void einEinzelnerWertLiefertImmerSichSelbst() {
        long[] einer = {4200};
        assertEquals(4200, Quantile.valueAt(einer, 0));
        assertEquals(4200, Quantile.valueAt(einer, 100));
    }

    @Test
    public void leereReiheLiefertNull() {
        assertEquals(0, Quantile.valueAt(new long[0], 50));
        assertEquals(0f, Quantile.percentOf(new long[0], 123), 0.01f);
    }

    @Test
    public void percentOfIstDieUmkehrungVonValueAt() {
        // Geprüft an den Rängen selbst: zehn Werte liegen auf 0, 11,1 … 100 %.
        for (int rang = 0; rang < ZEHN.length; rang++) {
            float prozent = 100f * rang / (ZEHN.length - 1);
            assertEquals(ZEHN[rang], Quantile.valueAt(ZEHN, prozent));
            assertEquals(prozent, Quantile.percentOf(ZEHN, ZEHN[rang]), 0.01f);
        }
    }

    /** Ein getippter Betrag zwischen zwei Werten zählt zum nächstniedrigeren Rang. */
    @Test
    public void getippterBetragZwischenZweiWertenLandetAufDemNaechstenRang() {
        assertEquals(Quantile.percentOf(ZEHN, 300), Quantile.percentOf(ZEHN, 350), 0.01f);
    }

    @Test
    public void dieNullLiegtZwischenAusgabenUndEinnahmen() {
        long[] gemischt = {-400, -300, -200, -100, 100};   // vier Ausgaben, eine Einnahme
        assertEquals(87.5f, Quantile.percentOfZero(gemischt), 0.01f);
        long[] haelfte = {-200, -100, 100, 200, 300};
        assertTrue(Quantile.percentOfZero(haelfte) > 0f);
        assertTrue(Quantile.percentOfZero(haelfte) < 100f);
    }

    @Test
    public void ohneVorzeichenwechselGibtEsKeineNullmarke() {
        assertEquals(-1f, Quantile.percentOfZero(ZEHN), 0.01f);                  // nur Einnahmen
        assertEquals(-1f, Quantile.percentOfZero(new long[]{-900, -100}), 0.01f); // nur Ausgaben
    }
}
