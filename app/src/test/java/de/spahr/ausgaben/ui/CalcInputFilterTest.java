package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Was sich in ein Betragsfeld tippen läßt ({@link CalcInputFilter}) – vor allem: nur das eingestellte
 * Dezimalzeichen, nicht beide.
 */
public class CalcInputFilterTest {

    private static boolean mitKomma(String s) {
        return CalcInputFilter.isTypablePrefix(s, ',');
    }

    private static boolean mitPunkt(String s) {
        return CalcInputFilter.isTypablePrefix(s, '.');
    }

    @Test
    public void mitKommaEinstellungGehtNurDasKomma() {
        assertTrue(mitKomma("12,50"));
        assertFalse(mitKomma("12.50"));
    }

    @Test
    public void mitPunktEinstellungGehtNurDerPunkt() {
        assertTrue(mitPunkt("12.50"));
        assertFalse(mitPunkt("12,50"));
    }

    @Test
    public void kleineRechnungenBleibenErlaubt() {
        assertTrue(mitKomma("10+20*3"));
        assertTrue(mitKomma("12,50+3,20"));
        assertTrue(mitKomma("10+"));      // die Zahl folgt noch
        assertTrue(mitKomma("-5"));
        assertTrue(mitKomma("3*-2"));
    }

    @Test
    public void zweitesTrennzeichenInDerselbenZahlWirdAbgelehnt() {
        assertFalse(mitKomma("1,2,3"));
        assertTrue(mitKomma("1,2+3,4"));  // zwei Zahlen, je ein Trennzeichen
    }

    @Test
    public void unerlaubteZeichenUndStrukturen() {
        assertFalse(mitKomma("12a"));
        assertFalse(mitKomma("(1+2)"));
        assertFalse(mitKomma("10/2"));
        assertFalse(mitKomma("+5"));
        assertFalse(mitKomma("1++2"));
        assertFalse(mitKomma("1,-"));
    }

    @Test
    public void leeresFeldIstEinGueltigerAnfang() {
        assertTrue(mitKomma(""));
    }
}
