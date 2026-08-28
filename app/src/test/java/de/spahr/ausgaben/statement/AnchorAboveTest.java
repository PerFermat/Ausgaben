package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Der Wert steht <b>über</b> seiner Beschriftung ({@link AnchorRule.Direction#LINE_ABOVE}).
 *
 * <p>Spiegelbild zu „darunter", und aus demselben Grund nötig: manche Belege setzen erst die Zahl und
 * benennen sie darunter — eine Summenzeile mit „Gesamtbetrag" als Unterschrift, oder eine Tabelle, deren
 * Spaltennamen unter den Daten stehen. Ohne diese Richtung ist eine solche Stelle gar nicht
 * anzusprechen.</p>
 */
public class AnchorAboveTest {

    /** Drei Zahlen übereinander, darunter die Beschriftung — jede Zeile ein anderer Abstand. */
    private static PdfText beleg() {
        return StatementFixtures.of(
                "Kurswert                    1.100,00 EUR",
                "Provision                       9,90 EUR",
                "Stückzinsen                     4,10 EUR",
                "Ausmachender Betrag");
    }

    private static AnchorRule darueber(String anchor, int distance) {
        return new AnchorRule(java.util.Collections.singletonList(anchor),
                AnchorRule.Direction.LINE_ABOVE, false, "", AnchorRule.Position.LAST, 1, distance);
    }

    @Test
    public void genauEineZeileDarueber() {
        assertEquals(4.10, darueber("Ausmachender Betrag", 1).read(beleg()), 0.0001);
    }

    @Test
    public void genauZweiZeilenDarueber() {
        assertEquals(9.90, darueber("Ausmachender Betrag", 2).read(beleg()), 0.0001);
    }

    @Test
    public void genauDreiZeilenDarueber() {
        assertEquals(1100.0, darueber("Ausmachender Betrag", 3).read(beleg()), 0.0001);
    }

    /** Ohne festen Abstand gilt die nächste Zeile darüber, die überhaupt einen Wert trägt. */
    @Test
    public void suchendNimmtDieNaechsteZeileMitEinemWert() {
        PdfText mitLuecke = StatementFixtures.of(
                "Kurswert                    1.100,00 EUR",
                "— keine Angabe —",
                "Ausmachender Betrag");
        assertEquals(1100.0, darueber("Ausmachender Betrag", 0).read(mitLuecke), 0.0001);
    }

    /** Weiter als drei Zeilen wird nicht gesucht — sonst zöge jede Regel beliebige Zahlen an. */
    @Test
    public void weiterAlsDreiZeilenWirdNichtGesucht() {
        PdfText weitWeg = StatementFixtures.of(
                "Kurswert                    1.100,00 EUR",
                "— keine Angabe —",
                "— keine Angabe —",
                "— keine Angabe —",
                "Ausmachender Betrag");
        assertNull(darueber("Ausmachender Betrag", 0).read(weitWeg));
    }

    /** Auch ein Datum: „18.08.2026" über der Zeile, die es benennt. */
    @Test
    public void auchDasDatumStehtManchmalDarueber() {
        PdfText beleg = StatementFixtures.of(
                "18.08.2026",
                "Handelstag");
        assertEquals(AnchorRule.single("Handelstag", AnchorRule.Direction.SAME_LINE)
                        .readDate(StatementFixtures.of("Handelstag 18.08.2026")),
                darueber("Handelstag", 1).readDate(beleg));
    }

    /** Die Gegenprobe: nach unten liest dieselbe Beschriftung weiterhin nichts anderes. */
    @Test
    public void nachUntenAendertSichNichts() {
        AnchorRule nachUnten = AnchorRule.single("Ausmachender Betrag",
                AnchorRule.Direction.LINE_BELOW);
        assertNull("unter der letzten Zeile steht nichts mehr", nachUnten.read(beleg()));

        PdfText andersherum = StatementFixtures.of(
                "Ausmachender Betrag",
                "Kurswert                    1.100,00 EUR");
        assertEquals("nach unten unverändert", 1100.0, nachUnten.read(andersherum), 0.0001);
        assertNull("und nach oben findet dieselbe Regel dort nichts",
                darueber("Ausmachender Betrag", 1).read(andersherum));
    }
}
