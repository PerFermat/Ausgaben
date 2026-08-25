package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.TextValues;

/**
 * Rückfall-Beschriftungen: eine Regel führt mehrere Beschriftungen in Rangfolge, und es gilt die erste,
 * die im Dokument einen Wert trägt.
 *
 * <p>Das ist die Antwort auf zwei Eigenheiten echter Abrechnungen: eine Bank druckt die Valuta-Zeile
 * nicht, wenn sie mit dem Zahltag zusammenfällt, und der gebuchte Bruttobetrag steht bei einem
 * dollarnotierten Papier in der Umrechnungszeile statt in der Bruttozeile.</p>
 */
public class AnchorFallbackTest {

    private static long tag(String date) {
        return TextValues.toDateMillis(date);
    }

    private static AnchorRule kette(String... anchors) {
        return new AnchorRule(Arrays.asList(anchors), AnchorRule.Direction.SAME_LINE, false);
    }

    // ---- Datum ----

    private static PdfText mitValuta() {
        return StatementFixtures.of(
                "Wertpapierabrechnung        Kauf",
                "Ausführungstag / -zeit      17.08.2026 um 09:04:58 Uhr",
                "Endbetrag zu Ihren Lasten   EUR             1.000,00",
                "Valuta                      19.08.2026");
    }

    private static PdfText ohneValuta() {
        return StatementFixtures.of(
                "Wertpapierabrechnung        Kauf",
                "Ausführungstag / -zeit      17.08.2026 um 09:04:58 Uhr",
                "Endbetrag zu Ihren Lasten   EUR             1.000,00");
    }

    @Test
    public void dieErsteBeschriftungGiltSolangeSieTrägt() {
        AnchorRule rule = kette("Valuta", "Ausführungstag / -zeit");
        assertEquals(tag("19.08.2026"), rule.readDate(mitValuta()));
        assertEquals("Valuta", rule.matchedAnchor(mitValuta()));
    }

    @Test
    public void fehltDieErsteSpringtDieZweiteEin() {
        AnchorRule rule = kette("Valuta", "Ausführungstag / -zeit");
        assertEquals(tag("17.08.2026"), rule.readDate(ohneValuta()));
        assertEquals("Ausführungstag / -zeit", rule.matchedAnchor(ohneValuta()));
    }

    /** Nicht „die unterste Zeile", sondern „die erste Beschriftung": die Rangfolge schlägt die Lage. */
    @Test
    public void dieRangfolgeSchlägtDieLageImDokument() {
        AnchorRule rule = kette("Ausführungstag / -zeit", "Valuta");
        // Die Valuta steht weiter unten – trotzdem gewinnt der Ausführungstag, weil er vorn steht.
        assertEquals(tag("17.08.2026"), rule.readDate(mitValuta()));
    }

    @Test
    public void trägtKeineBleibtEsLeer() {
        assertEquals(-1, kette("Zahltag", "Ex-Tag").readDate(mitValuta()));
        assertNull(kette("Zahltag", "Ex-Tag").matchedAnchor(mitValuta()));
    }

    // ---- Brutto in zwei Währungen ----

    private static PdfText dollarPapier() {
        return StatementFixtures.of(
                "Ertragsgutschrift",
                "Brutto                            USD           1.053,47",
                "Umg. z. Dev.-Kurs (1,161497)      EUR             906,99",
                "Kapitalertragsteuer 25,00%        EUR             158,73",
                "Gesamtbetrag zu Ihren Gunsten     EUR             739,53");
    }

    private static PdfText euroPapier() {
        return StatementFixtures.of(
                "Ertragsgutschrift",
                "Brutto                            EUR             500,00",
                "Kapitalertragsteuer 25,00%        EUR             125,00",
                "Gesamtbetrag zu Ihren Gunsten     EUR             375,00");
    }

    /**
     * Der Fall, um den es geht: dieselbe Regel muss bei einem Dollar-Papier den umgerechneten Betrag
     * lesen und bei einem Euro-Papier den Bruttobetrag selbst.
     */
    @Test
    public void dieselbeKetteLiestDollarUndEuroBrutto() {
        AnchorRule brutto = new AnchorRule(Arrays.asList("Umg. z. Dev.-Kurs", "Brutto"),
                AnchorRule.Direction.SAME_LINE, false, "EUR");
        assertEquals(Long.valueOf(90699L), brutto.readCents(dollarPapier()));
        assertEquals("Umg. z. Dev.-Kurs", brutto.matchedAnchor(dollarPapier()));
        assertEquals(Long.valueOf(50000L), brutto.readCents(euroPapier()));
        assertEquals("Brutto", brutto.matchedAnchor(euroPapier()));
    }

    /**
     * Die Währungsbindung sichert dasselbe noch einmal von der anderen Seite ab: stünde die
     * Umrechnungszeile nicht in der Kette, würde der Dollarbetrag nicht etwa als Euro gelesen.
     */
    @Test
    public void einBetragInFremderWährungWirdNichtGenommen() {
        AnchorRule nurBrutto = new AnchorRule(Arrays.asList("Brutto"),
                AnchorRule.Direction.SAME_LINE, false, "EUR");
        assertNull(nurBrutto.readCents(dollarPapier()));
        assertEquals(Long.valueOf(50000L), nurBrutto.readCents(euroPapier()));
    }

    // ---- Summen bleiben Summen ----

    @Test
    public void beimSummierenTragenAlleBeschriftungenZusammen() {
        AnchorRule steuer = AnchorRule.summed(
                Arrays.asList("Kapitalertragsteuer", "Solidaritätszuschlag"),
                AnchorRule.Direction.SAME_LINE, "EUR");
        // Beide Zeilen vorhanden: zusammengezählt, nicht „die erste, die trägt".
        assertEquals(Long.valueOf(16746L), steuer.readCents(StatementFixtures.ingDividende()));
        // Nur eine vorhanden: dann eben nur diese.
        assertEquals(Long.valueOf(15873L), steuer.readCents(dollarPapier()));
    }

    // ---- Vorlage: Treffer zählen und Ketten ergänzen ----

    private static StatementTemplate template(Map<StatementTemplate.Field, AnchorRule> rules) {
        return new StatementTemplate(StatementScan.DIVIDEND, rules);
    }

    @Test
    public void eineLangeKetteMachtEineVorlageNichtTreffender() {
        Map<StatementTemplate.Field, AnchorRule> karg =
                new EnumMap<>(StatementTemplate.Field.class);
        karg.put(StatementTemplate.Field.NET, kette("Gesamtbetrag zu Ihren Gunsten"));
        karg.put(StatementTemplate.Field.DATE, kette("Zahltag", "Valuta", "Ex-Tag"));

        Map<StatementTemplate.Field, AnchorRule> reich =
                new EnumMap<>(StatementTemplate.Field.class);
        reich.put(StatementTemplate.Field.NET, kette("Gesamtbetrag zu Ihren Gunsten"));
        reich.put(StatementTemplate.Field.DATE, kette("Zahltag"));
        reich.put(StatementTemplate.Field.FEE, kette("Kapitalertragsteuer"));
        reich.put(StatementTemplate.Field.SHARES, kette("Nominale"));

        PdfText text = StatementFixtures.ingDividende();
        // Die karge Vorlage hat mehr Beschriftungen, die reichere mehr Felder – gezählt werden Felder.
        assertTrue(template(reich).score(text) > template(karg).score(text));
    }

    @Test
    public void gelerntesWirdHintenAngehängtNichtVorn() {
        Map<StatementTemplate.Field, AnchorRule> alt = new EnumMap<>(StatementTemplate.Field.class);
        alt.put(StatementTemplate.Field.DATE, kette("Valuta"));
        Map<StatementTemplate.Field, AnchorRule> neu = new EnumMap<>(StatementTemplate.Field.class);
        neu.put(StatementTemplate.Field.DATE, kette("Zahltag"));

        AnchorRule merged = template(neu).appendedTo(template(alt))
                .rule(StatementTemplate.Field.DATE);
        assertEquals(Arrays.asList("Valuta", "Zahltag"), merged.anchors);
    }

    @Test
    public void einBekanntesGliedWirdNichtDoppeltAngehängt() {
        Map<StatementTemplate.Field, AnchorRule> alt = new EnumMap<>(StatementTemplate.Field.class);
        alt.put(StatementTemplate.Field.DATE, kette("Valuta", "Zahltag"));
        Map<StatementTemplate.Field, AnchorRule> neu = new EnumMap<>(StatementTemplate.Field.class);
        neu.put(StatementTemplate.Field.DATE, kette("Zahltag"));

        assertEquals(Arrays.asList("Valuta", "Zahltag"),
                template(neu).appendedTo(template(alt)).rule(StatementTemplate.Field.DATE).anchors);
    }
}
