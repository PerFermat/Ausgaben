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

    /**
     * Regression: {@code Math.round(wert * 100.0)} verschiebt krumme Beträge um einen Cent, weil
     * {@code 2,675} als {@code double} {@code 2.67499999999999982} ist. {@code AnchorRule.readCents} und
     * die Selbstprüfung in {@code TemplateCheck} rechneten so — und weil <b>beide</b> denselben Fehler
     * machten, fiel er der Probe aufs Gelernte nicht auf.
     */
    @Test
    public void centsOfRundetWieAufDemBeleg() {
        assertEquals(268L, TextValues.centsOf(2.675));
        assertEquals(-268L, TextValues.centsOf(-2.675));
        assertEquals(115L, TextValues.centsOf(1.145));
        assertEquals(90699L, TextValues.centsOf(906.99));
        assertEquals(0L, TextValues.centsOf(0.0));
    }

    /** Beide Wege müssen zur selben Zahl führen, sonst widersprächen sich Auslese und Prüfung. */
    @Test
    public void centsOfUndToCentsStimmenUeberein() {
        for (String betrag : new String[]{"2,675", "1,145", "906,99", "-32,09", "1.053,47", "0,005"}) {
            assertEquals("bei " + betrag,
                    TextValues.toCents(betrag).longValue(),
                    TextValues.centsOf(TextValues.toDecimal(betrag)));
        }
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

    /**
     * Regression: {@code SimpleDateFormat.parse(String)} liest nur das <b>Präfix</b> und ist zufrieden,
     * sobald der Anfang passt — Resttext fällt still unter den Tisch. Ein Datumstoken mit angehängter
     * Uhrzeit oder Referenz ist auf Abrechnungen die Regel („Schlusstag/-Zeit"), und der Javadoc der
     * Klasse führt {@code 11.6.2022-01:30:01} ausdrücklich als Beispiel dafür auf, was <b>nicht</b>
     * durchgehen soll.
     */
    @Test
    public void einDatumMitAngehaengtemTextIstKeinDatum() {
        assertEquals(-1, TextValues.toDateMillis("11.6.2022-01:30:01"));
        assertEquals(-1, TextValues.toDateMillis("17.08.2026 um 09:04:58"));
        assertEquals(-1, TextValues.toDateMillis("2026-08-17T10:00"));
        assertEquals(-1, TextValues.toLedgerDateMillis("17.08.2026 Rest"));
    }

    /**
     * Der schwerere Fall desselben Fehlers: {@code SLASH_DATE} in {@code toUnambiguousDateMillis} ist mit
     * {@code ^…$} verankert und griff bei angehängter Uhrzeit deshalb gar nicht. Die Mehrdeutigkeit
     * blieb unbemerkt, und das Muster {@code MM/dd/yyyy} lieferte den 8. März — eine falsche Zahl statt
     * eines leeren Feldes, das der Nutzer ausfüllt.
     */
    @Test
    public void dieMehrdeutigkeitssperreLaesstSichNichtMitUhrzeitUmgehen() {
        assertEquals(-1, TextValues.toUnambiguousDateMillis("03/08/2026 12:00"));
        assertEquals(-1, TextValues.toUnambiguousDateMillis("03/08/2026-01:30:01"));
    }

    /**
     * Die beiden Wege gegeneinander festgenagelt: was auf einem Bankbeleg steht, darf in einer
     * Ledger-Datei nicht als Datum durchgehen. Sonst gäbe sich ein fremder Kontoauszug als Export dieser
     * App aus, statt abgelehnt zu werden.
     */
    @Test
    public void belegformateGeltenNichtImLedger() {
        assertTrue(TextValues.toDateMillis("04 Nov 2009") > 0);
        assertEquals(-1, TextValues.toLedgerDateMillis("04 Nov 2009"));
        assertEquals(-1, TextValues.toLedgerDateMillis("05-12-2019"));
        assertEquals(-1, TextValues.toLedgerDateMillis("06/29/22"));
    }

    /** Was der eigene Export und die KMyMoney-Berichte schreiben, liest der Ledger-Weg weiterhin. */
    @Test
    public void ledgerformateBleibenLesbar() {
        assertEquals(17, ymd(TextValues.toLedgerDateMillis("17.08.2026"))[2]);
        assertEquals(17, ymd(TextValues.toLedgerDateMillis("2026-08-17"))[2]);
        assertEquals(17, ymd(TextValues.toLedgerDateMillis("2026/08/17"))[2]);
        assertEquals(17, ymd(TextValues.toLedgerDateMillis("08/17/2026"))[2]);
        assertEquals(17, ymd(TextValues.toLedgerDateMillis("17/08/2026"))[2]);
    }
    /**
     * Nur ein wirklich vergebenes Kürzel darf abgeschnitten werden.
     *
     * <p>Bis 1.12 galt jede Endung aus drei Großbuchstaben als Währung. Aus {@code 1437STK} („Stück")
     * wurde damit die Zahl 1437 und aus {@code 2019DEC} die Zahl 2019 — Scheinzahlen, die anschließend
     * als Kandidaten in die Regelsuche wandern und dort die abgezählte Stelle verschieben. Der Fehler
     * zeigt sich also nicht da, wo er entsteht.</p>
     */
    @Test
    public void einStueckzusatzIstKeineWaehrung() {
        assertNull(TextValues.toCents("1437STK"));
        assertNull(TextValues.toCents("2019DEC"));
        // Und damit auch nicht als Zahl der Zeile: die Regelsuche sieht solche Wörter gar nicht erst.
        assertTrue(TextValues.numberTokens("Nominale 1437STK Kurswert 926,40 EUR").size() == 1);
    }

    /** Echte Kürzel werden weiterhin abgeschnitten – hinten wie vorn. */
    @Test
    public void echteWaehrungskuerzelStehenDerZahlNichtImWeg() {
        assertEquals(Long.valueOf(92640L), TextValues.toCents("926,40EUR"));
        assertEquals(Long.valueOf(15873L), TextValues.toCents("EUR158,73"));
        assertEquals(Long.valueOf(-92640L), TextValues.toCents("926,40-EUR"));
        assertEquals(Long.valueOf(10000L), TextValues.toCents("100.00CHF"));
    }

    // ---- Prozentsatz aus einem Eingabefeld ----

    /** Ein leeres Feld ist eine Ansage: die Vorbelegung soll aus. */
    @Test
    public void einLeeresFeldSchaltetDieVorbelegungAb() {
        assertEquals(Double.valueOf(0.0), TextValues.percentOrNull(""));
        assertEquals(Double.valueOf(0.0), TextValues.percentOrNull("   "));
        assertEquals(Double.valueOf(0.0), TextValues.percentOrNull(null));
    }

    /** Komma wie Punkt – das Zahlenformat ist einstellbar, die Eingabe soll beides annehmen. */
    @Test
    public void gueltigeSaetzeErgebenSichSelbst() {
        assertEquals(26.375, TextValues.percentOrNull("26,375"), 1e-9);
        assertEquals(26.375, TextValues.percentOrNull("26.375"), 1e-9);
        assertEquals(25.0, TextValues.percentOrNull(" 25 "), 1e-9);
        assertEquals(99.99, TextValues.percentOrNull("99,99"), 1e-9);
    }

    /**
     * Und der eigentliche Punkt: eine Fehleingabe darf nicht als Löschung wirken.
     *
     * <p>Bis 1.12 ergaben alle diese Eingaben eine 0, und die wurde gespeichert. Wer sich vertippte,
     * hatte seine Steuervorbelegung danach kommentarlos abgeschaltet — ohne einen Hinweis, dass etwas
     * nicht gelesen werden konnte. {@code null} heißt jetzt „lass den gespeicherten Wert in Ruhe".</p>
     */
    @Test
    public void einUnlesbarerSatzLoeschtNichts() {
        assertNull("Text", TextValues.percentOrNull("sechsundzwanzig"));
        assertNull("zwei Trennzeichen", TextValues.percentOrNull("26.37.5"));
        assertNull("über 100", TextValues.percentOrNull("126"));
        assertNull("genau 100", TextValues.percentOrNull("100"));
        assertNull("negativ", TextValues.percentOrNull("-5"));
    }

    /** Die ausdrücklich getippte 0 ist dagegen keine Fehleingabe, sondern dasselbe wie ein leeres Feld. */
    @Test
    public void dieGetippteNullSchaltetEbenfallsAb() {
        assertEquals(Double.valueOf(0.0), TextValues.percentOrNull("0"));
        assertEquals(Double.valueOf(0.0), TextValues.percentOrNull("0,00"));
    }

    // ---- Zwei Schreibweisen für „negativ" heben sich nicht auf ----

    /**
     * {@code (5,00)} ist die angelsächsische Schreibweise für minus fünf, {@code -5,00} die
     * gewöhnliche. Stehen beide zusammen, ist die Zahl immer noch negativ — bis 1.12 hoben sie
     * einander auf und {@code (-5,00)} wurde zu <b>plus</b> 5,00.
     */
    @Test
    public void klammerUndMinusHebenSichNichtAuf() {
        assertEquals(Long.valueOf(-500L), TextValues.toCents("(5,00)"));
        assertEquals(Long.valueOf(-500L), TextValues.toCents("-5,00"));
        assertEquals(Long.valueOf(-500L), TextValues.toCents("(-5,00)"));
        assertEquals("auch mit nachgestelltem Vorzeichen", Long.valueOf(-500L),
                TextValues.toCents("(5,00-)"));
    }

    /** Positiv bleibt positiv – die Änderung darf nicht alles negativ machen. */
    @Test
    public void ohneMinuszeichenBleibtEsPositiv() {
        assertEquals(Long.valueOf(500L), TextValues.toCents("5,00"));
        assertEquals(Long.valueOf(500L), TextValues.toCents("+5,00"));
        assertEquals(Long.valueOf(500L), TextValues.toCents("5,00+"));
    }

    // ---- „und keine Cent" ----

    /**
     * {@code 1.242,--} ist österreichisch für „und keine Cent". Mit <b>Punkt</b> als Dezimalzeichen
     * ({@code 1.242.--}) scheiterte es bis 1.12: Daraus wurde {@code 1.242.00}, und zwei Punkte kann
     * der Parser nicht auseinanderhalten — was er auch nicht soll, sonst ginge eine Belegnummer wie
     * {@code 1.234.567} als Betrag durch.
     */
    @Test
    public void ohneCentInBeidenSchreibweisen() {
        assertEquals(Long.valueOf(124_200L), TextValues.toCents("1.242,--"));
        assertEquals(Long.valueOf(124_200L), TextValues.toCents("1.242.--"));
        assertEquals(Long.valueOf(124_200L), TextValues.toCents("1,242.--"));
        assertEquals(Long.valueOf(24_200L), TextValues.toCents("242,--"));
    }

    /** Und Belegnummern bleiben draußen – die strenge Lesart gilt weiter. */
    @Test
    public void belegnummernGehenWeiterhinNichtDurch() {
        assertNull(TextValues.toCents("1.234.567"));
        assertNull(TextValues.toCents("0993.01010100.0000346ER02"));
    }
}
