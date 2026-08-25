package de.spahr.ausgaben.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Wie Banken Zahlen schreiben — und was ihnen nur ähnlich sieht.
 *
 * <p>Alle Fälle stammen aus einem Abgleich mit rund 2600 echten Abrechnungen aus über hundert Banken.
 * Sie stehen hier als eigene Beispiele, damit sie auch ohne diesen Bestand geprüft werden
 * ({@code StatementCorpusTest} überspringt sich ohne ihn).</p>
 *
 * <p>Die zweite Hälfte ist die wichtigere: eine Abrechnung ist voll von Auftrags-, Depot- und
 * Referenznummern, die einer Zahl gleichen. Würde der Parser die annehmen, läse
 * {@code AnchorRule} irgendeine davon als Betrag.</p>
 */
public class BankNumberFormatsTest {

    // ---- Was gelesen werden muss ----

    @Test
    public void nachgestelltesVorzeichenGehoertZurZahl() {
        // DKB, Postbank, onvista, comdirect, S-Broker: das Minus steht hinter dem Betrag.
        assertEquals(Long.valueOf(-3209L), TextValues.toCents("32,09-"));
        assertEquals(Long.valueOf(14452L), TextValues.toCents("144,52+"));
        assertEquals(Long.valueOf(-203066L), TextValues.toCents("2.030,66-"));
    }

    @Test
    public void auchMitWaehrungAmWort() {
        // genobroker: „Provision 32,95-EUR"
        assertEquals(Long.valueOf(-3295L), TextValues.toCents("32,95-EUR"));
        assertEquals(Long.valueOf(92640L), TextValues.toCents("926,40EUR"));
    }

    @Test
    public void apostrophTrenntTausenderInDerSchweiz() {
        assertEquals(Long.valueOf(442000L), TextValues.toCents("4'420.00"));
        assertEquals(Long.valueOf(-1227455L), TextValues.toCents("-12'274.55"));
        // Auch der typografische Apostroph kommt vor.
        assertEquals(Long.valueOf(442000L), TextValues.toCents("4’420.00"));
    }

    @Test
    public void waehrungszeichenDirektAnDerZahl() {
        assertEquals(Long.valueOf(208394L), TextValues.toCents("€2.083,94"));
        assertEquals(Long.valueOf(177741L), TextValues.toCents("£1,777.41"));
        assertEquals(Long.valueOf(102992L), TextValues.toCents("$1,029.92"));
    }

    @Test
    public void oesterreichischesNullkomma() {
        // easybank, Hello Bank, DADAT: „Kurswert: -1.100,-- GBP"
        assertEquals(Long.valueOf(124200L), TextValues.toCents("1.242,--"));
        assertEquals(Long.valueOf(-110000L), TextValues.toCents("-1.100,--"));
    }

    @Test
    public void klammernBedeutenNegativ() {
        assertEquals(Long.valueOf(-123456L), TextValues.toCents("(1.234,56)"));
    }

    @Test
    public void dieBisherigenFormateBleibenWieSieWaren() {
        assertEquals(Long.valueOf(100000L), TextValues.toCents("1.000,00"));
        assertEquals(Long.valueOf(100000L), TextValues.toCents("1,000.00"));
        assertEquals(Long.valueOf(-500L), TextValues.toCents("-5,00"));
        assertEquals(1839.80185, TextValues.toDecimal("1.839,80185"), 1e-9);
    }

    // ---- Was abgelehnt bleiben muss ----

    @Test
    public void auftragsUndReferenznummernSindKeineBetraege() {
        assertNull(TextValues.toCents("495752/48.00"));            // Auftragsnummer
        assertNull(TextValues.toCents("0993.01010100.0000346ER02"));   // Referenz der DKB
        assertNull(TextValues.toCents("1.234.567.890"));           // Depotnummer
        assertNull(TextValues.toCents("0.123.123.01"));            // Schweizer Kontonummer
        assertNull(TextValues.toCents("CHE-105.779.532"));         // UID-Nummer
        assertNull(TextValues.toCents("11.6.2022-01:30:01"));      // Zeitstempel
        assertNull(TextValues.toCents("GH.20200121.021202.5018.0014.2959"));
        assertNull(TextValues.toCents("36011270-11.9.2017"));
    }

    @Test
    public void einProzentsatzIstKeinBetrag() {
        // „Kapitalertragsteuer 24,45 % auf 131,25 EUR 32,09- EUR" – der Satz darf nie als Wert gelten.
        assertNull(TextValues.toCents("24,45%"));
        assertNull(TextValues.toCents("0,0000%"));
    }

    @Test
    public void einBlossesWaehrungskuerzelIstKeineZahl() {
        assertNull(TextValues.toCents("EUR"));
        assertNull(TextValues.toCents(""));
        assertNull(TextValues.toCents("-"));
    }
}
