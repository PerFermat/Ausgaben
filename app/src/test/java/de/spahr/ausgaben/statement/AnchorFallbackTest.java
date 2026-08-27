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

    // ---- Welche Zahl der Zeile ----

    /**
     * Eine Tabelle mit Spaltenüberschrift: der Wert führt die Zeile an, rechts stehen Kurs und
     * Kennnummer. Nachgebaut aus einer Anleihe-Abrechnung; an einem Bestand fremder Belege kommt die
     * Stückzahl in 876 von 2354 Fällen <b>nur</b> so vor.
     */
    private static PdfText tabelle() {
        return StatementFixtures.of(
                "Wertpapier Abrechnung Kauf",
                "Nominale Wertpapierbezeichnung ISIN (WKN)",
                "EUR 2.000,00 8,75 % METALCORP GROUP B.V. DE000A1HLTD2 (A1HLTD)",
                "Kurswert 1.950,00- EUR",
                "Ausmachender Betrag 2.030,66- EUR");
    }

    @Test
    public void unterEinerSpaltenueberschriftGiltDieErsteZahl() {
        AnchorRule nominale = new AnchorRule(Arrays.asList("Nominale"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.FIRST);
        assertEquals(2000.0, nominale.read(tabelle()), 1e-9);

        // Dieselbe Regel mit der letzten Zahl läse den Kurs von 8,75 statt der Nominale.
        AnchorRule letzte = new AnchorRule(Arrays.asList("Nominale"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.LAST);
        assertEquals(8.75, letzte.read(tabelle()), 1e-9);
    }

    /** Der Lerner findet die Stelle selbst — er probiert erst die letzte Zahl, dann die erste. */
    @Test
    public void derLernerFindetDieSpalteVonSelbst() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 2000.0;
        k.netCents = 203066L;
        StatementTemplate t = TemplateLearner.learn(tabelle(), k);

        assertEquals(2000.0, t.apply(tabelle()).shares, 1e-9);
        assertEquals(Long.valueOf(203066L), t.apply(tabelle()).netCents);
        // Und die Beschriftung ist die Überschrift, nicht das Währungskürzel, mit dem die Wertzeile
        // beginnt — „EUR" wäre als Anker wertlos, es steht in jeder zweiten Zeile.
        assertEquals("Nominale Wertpapierbezeichnung ISIN (WKN)",
                t.rule(StatementTemplate.Field.SHARES).anchors.get(0));
    }

    /**
     * Zwei Felder in einer Zeile: „St. 1.437 EUR 37,22" nennt vorn die Stückzahl und hinten den Kurs.
     * Beide müssen dieselbe Beschriftung benutzen dürfen — sie meinen verschiedene Zahlen.
     */
    @Test
    public void eineZeileKannZweiFelderTragen() {
        PdfText beleg = StatementFixtures.of(
                "Wertpapierkauf",
                "St.  250                  EUR  37,22",
                "Kurswert                    : EUR            9.305,00",
                "Zu Ihren Lasten                          : EUR     9.305,00");
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 250.0;
        k.price = 37.22;
        k.netCents = 930500L;
        StatementTemplate t = TemplateLearner.learn(beleg, k);

        StatementTemplate.Extraction e = t.apply(beleg);
        assertEquals(250.0, e.shares, 1e-9);
        assertEquals(37.22, e.price, 1e-9);
        assertEquals(Long.valueOf(930500L), e.netCents);
    }

    /** Beträge werden ohne Vorzeichen gelesen — das Minus gehört zur Buchhaltung, nicht zum Feld. */
    @Test
    public void dasVorzeichenDerBankGehoertNichtZumWert() {
        AnchorRule kurswert = kette("Kurswert");
        assertEquals(1950.0, kurswert.read(tabelle()), 1e-9);
    }

    // ---- Tabellenzeilen: die n-te Zahl ----

    /**
     * Eine amerikanische Wertpapierabrechnung: Kopfzeile mit Spaltennamen, darunter die Zeile mit acht
     * Angaben. Der Kurs ist die letzte Zahl, die Menge die vorletzte — ohne die Stelle in der Zeile wäre
     * sie nicht auszudrücken.
     */
    private static PdfText usAbrechnung() {
        return StatementFixtures.of(
                "We are pleased to confirm the following transaction",
                "ACTION SYMBOL CUSIP DATE DATE TYPE QUANTITY PRICE",
                "YOU BOUGHT XBI 78464A870 06/29/22 07/01/22 MARGIN 5 $74.33000",
                "SPDR SER TR S&P BIOTECH ETF PRINCIPAL $371.65",
                "UNSOLICITED NET AMOUNT $371.65");
    }

    @Test
    public void dieVorletzteZahlDerZeile() {
        AnchorRule menge = new AnchorRule(Arrays.asList("MARGIN"),
                AnchorRule.Direction.SAME_LINE, false, "", AnchorRule.Position.LAST, 2);
        assertEquals(5.0, menge.read(usAbrechnung()), 1e-9);

        AnchorRule kurs = kette("MARGIN");
        assertEquals(74.33, kurs.read(usAbrechnung()), 1e-9);
    }

    /** Der Lerner findet die Stelle selbst — er zählt von aussen nach innen. */
    @Test
    public void derLernerZaehltInDieZeileHinein() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 5.0;
        k.price = 74.33;
        k.netCents = 37165L;
        StatementTemplate t = TemplateLearner.learn(usAbrechnung(), k);

        StatementTemplate.Extraction e = t.apply(usAbrechnung());
        assertEquals(5.0, e.shares, 1e-9);
        assertEquals(74.33, e.price, 1e-9);
        assertEquals(Long.valueOf(37165L), e.netCents);
    }

    /**
     * Die Währung wird aus dem Text <b>hinter</b> der Beschriftung gelernt, nicht aus der ganzen Zeile.
     * Sonst nähme „UNSOLICITED NET AMOUNT $371.65" das Wort „NET" für ein Währungskürzel und fände den
     * Betrag nie wieder.
     */
    @Test
    public void keinEnglischesWortAlsWaehrung() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.netCents = 37165L;
        StatementTemplate t = TemplateLearner.learn(usAbrechnung(), k);
        assertEquals("", t.rule(StatementTemplate.Field.NET).currency);
        assertEquals(Long.valueOf(37165L), t.apply(usAbrechnung()).netCents);
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
    public void gelerntesWirdHintenAngehängtWennAlteRegelNichtsFand() {
        Map<StatementTemplate.Field, AnchorRule> alt = new EnumMap<>(StatementTemplate.Field.class);
        alt.put(StatementTemplate.Field.DATE, kette("Valuta"));
        Map<StatementTemplate.Field, AnchorRule> neu = new EnumMap<>(StatementTemplate.Field.class);
        neu.put(StatementTemplate.Field.DATE, kette("Zahltag"));

        // "Valuta" kommt im Dokument nicht vor – die alte Regel fand also nichts.
        AnchorRule merged = template(neu).appendedTo(template(alt), PdfText.fromLines("Zahltag 100,00"))
                .rule(StatementTemplate.Field.DATE);
        assertEquals(Arrays.asList("Valuta", "Zahltag"), merged.anchors);
    }

    @Test
    public void gelerntesWirdVornAngehängtWennAlteRegelEtwasFand() {
        Map<StatementTemplate.Field, AnchorRule> alt = new EnumMap<>(StatementTemplate.Field.class);
        alt.put(StatementTemplate.Field.DATE, kette("Valuta"));
        Map<StatementTemplate.Field, AnchorRule> neu = new EnumMap<>(StatementTemplate.Field.class);
        neu.put(StatementTemplate.Field.DATE, kette("Zahltag"));

        // "Valuta" steht im Dokument – die alte Regel hätte (falsch) etwas gefunden.
        AnchorRule merged = template(neu)
                .appendedTo(template(alt), PdfText.fromLines("Valuta 01.01.2024\nZahltag 100,00"))
                .rule(StatementTemplate.Field.DATE);
        assertEquals(Arrays.asList("Zahltag", "Valuta"), merged.anchors);
    }

    @Test
    public void einBekanntesGliedWirdNichtDoppeltAngehängt() {
        Map<StatementTemplate.Field, AnchorRule> alt = new EnumMap<>(StatementTemplate.Field.class);
        alt.put(StatementTemplate.Field.DATE, kette("Valuta", "Zahltag"));
        Map<StatementTemplate.Field, AnchorRule> neu = new EnumMap<>(StatementTemplate.Field.class);
        neu.put(StatementTemplate.Field.DATE, kette("Zahltag"));

        assertEquals(Arrays.asList("Valuta", "Zahltag"),
                template(neu).appendedTo(template(alt), PdfText.fromLines("")).rule(StatementTemplate.Field.DATE).anchors);
    }
}
