package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Was sich in ein Betragsfeld tippen läßt ({@link CalcInputFilter}) – vor allem: nur das eingestellte
 * Dezimalzeichen, nicht beide.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
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

    /**
     * Aus einer PDF-Rechnung kopierte Beträge tragen oft ein Tausendertrennzeichen (das jeweils andere
     * der beiden möglichen Zeichen) – das wird beim Einfügen entfernt statt die ganze Einfügung
     * abzulehnen.
     */
    @Test
    public void tausenderpunktBeiKommaEinstellungWirdEntfernt() {
        EditText field = new EditText(ApplicationProvider.getApplicationContext());
        field.setKeyListener(DigitsKeyListener.getInstance("0123456789,.+-*"));
        field.setFilters(new InputFilter[]{new CalcInputFilter(',')});
        field.getText().replace(0, 0, "1.000,00");
        assertEquals("1000,00", field.getText().toString());
    }

    @Test
    public void tausenderkommaBeiPunktEinstellungWirdEntfernt() {
        EditText field = new EditText(ApplicationProvider.getApplicationContext());
        field.setKeyListener(DigitsKeyListener.getInstance("0123456789.,+-*"));
        field.setFilters(new InputFilter[]{new CalcInputFilter('.')});
        field.getText().replace(0, 0, "1,000.00");
        assertEquals("1000.00", field.getText().toString());
    }
}
