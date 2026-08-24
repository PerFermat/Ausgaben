package de.spahr.ausgaben.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

/**
 * Zahlen und Datumsangaben aus fremdem Text. Die Trennzeichen-Erkennung stammt aus dem CSV-Import und
 * gilt unverändert weiter; neu sind die Dezimalzahl ohne Cent-Rundung (Stückzahlen) und die Weigerung,
 * ein mehrdeutiges Datum zu raten.
 */
public class TextValuesTest {

    // ---- Beträge ----

    @Test
    public void deutschesFormatMitTausenderpunkt() {
        assertEquals(Long.valueOf(100000L), TextValues.toCents("1.000,00"));
    }

    @Test
    public void englischesFormatMitTausenderkomma() {
        assertEquals(Long.valueOf(100000L), TextValues.toCents("1,000.00"));
    }

    @Test
    public void nurKommaGiltAlsDezimaltrenner() {
        assertEquals(Long.valueOf(52600L), TextValues.toCents("526,00"));
    }

    @Test
    public void geschützteLeerzeichenStörenNicht() {
        assertEquals(Long.valueOf(100000L), TextValues.toCents("1.000,00 "));
    }

    @Test
    public void negativeBeträgeBehaltenIhrVorzeichen() {
        assertEquals(Long.valueOf(-2550L), TextValues.toCents("-25,50"));
    }

    @Test
    public void unlesbaresLiefertNull() {
        assertNull(TextValues.toCents("EUR"));
        assertNull(TextValues.toCents(""));
        assertNull(TextValues.toCents(null));
    }

    // ---- Dezimalzahlen (Stückzahlen, Kurse) ----

    /**
     * Der Kern des Herauslösens: eine Stückzahl hat mehr Nachkommastellen als Geld. Über
     * {@link TextValues#toCents} gelesen wären aus 1.839,80185 Stück glatte 1.839,80 geworden.
     */
    @Test
    public void stückzahlBehältAlleNachkommastellen() {
        assertEquals(1839.80185, TextValues.toDecimal("1.839,80185"), 1e-9);
        assertEquals(6.09607, TextValues.toDecimal("6,09607"), 1e-9);
    }

    @Test
    public void dezimalzahlAuchImEnglischenFormat() {
        assertEquals(1839.80185, TextValues.toDecimal("1,839.80185"), 1e-9);
    }

    @Test
    public void dezimalzahlOhneTrennzeichen() {
        assertEquals(20.0, TextValues.toDecimal("20"), 1e-9);
    }

    // ---- Datum ----

    private static int[] ymd(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
    }

    @Test
    public void deutschesUndIsoDatum() {
        assertEquals(2026, ymd(TextValues.toDateMillis("17.08.2026"))[0]);
        assertEquals(8, ymd(TextValues.toDateMillis("17.08.2026"))[1]);
        assertEquals(17, ymd(TextValues.toDateMillis("17.08.2026"))[2]);
        assertEquals(17, ymd(TextValues.toDateMillis("2026-08-17"))[2]);
    }

    @Test
    public void datumStehtAufMitternacht() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(TextValues.toDateMillis("17.08.2026"));
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
        assertEquals(0, c.get(Calendar.MILLISECOND));
    }

    @Test
    public void unlesbaresDatumLiefertMinusEins() {
        assertEquals(-1, TextValues.toDateMillis("Xetra"));
        assertEquals(-1, TextValues.toDateMillis(""));
    }

    /** 03/08/2026 kann der 3. August oder der 8. März sein – dann lieber gar nichts. */
    @Test
    public void mehrdeutigesSchrägstrichdatumWirdVerweigert() {
        assertEquals(-1, TextValues.toUnambiguousDateMillis("03/08/2026"));
        assertEquals(-1, TextValues.toUnambiguousDateMillis("1/2/2026"));
    }

    @Test
    public void eindeutigesSchrägstrichdatumGehtDurch() {
        // 17 kann kein Monat sein, also ist die Sache entschieden.
        long millis = TextValues.toUnambiguousDateMillis("08/17/2026");
        assertTrue(millis > 0);
        assertEquals(17, ymd(millis)[2]);
        assertEquals(8, ymd(millis)[1]);
    }

    @Test
    public void gleicheZahlenSindNichtMehrdeutig() {
        // 05/05/2026 ist in beiden Lesarten derselbe Tag.
        assertTrue(TextValues.toUnambiguousDateMillis("05/05/2026") > 0);
    }

    @Test
    public void punktUndIsoDatumSindNieMehrdeutig() {
        assertTrue(TextValues.toUnambiguousDateMillis("03.08.2026") > 0);
        assertTrue(TextValues.toUnambiguousDateMillis("2026-08-03") > 0);
    }
}
