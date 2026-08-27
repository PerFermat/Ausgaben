package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Eine Gebühr, die die Bank nicht ausdruckt.
 *
 * <p>Scalable Capital nimmt je Order einen festen Betrag, der in der Abrechnung nirgends steht. Eine
 * Ankerregel kann ihn nicht finden — sie sucht nach einer Beschriftung. Er gehört deshalb an die Vorlage,
 * und dort entscheidet der vorhandene Schalter «Mehrere Zeilen zusammenzählen» über sein Schicksal.</p>
 */
public class FixedFeeTest {

    /** Ein Kauf ohne jede Gebührenzeile — so sieht die Abrechnung aus, um die es geht. */
    private static PdfText ohneGebuehr() {
        return text(
                "Wertpapierabrechnung Kauf",
                "ISIN                        IE00B3RBWM25",
                "Stück                       6,09607",
                "Kurs                        EUR 164,04",
                "Gesamtbetrag                EUR 1.000,00");
    }

    /** Derselbe Kauf, aber mit ausgewiesener Steuer — für den Fall «beides vorhanden». */
    private static PdfText mitGebuehr() {
        return text(
                "Wertpapierabrechnung Kauf",
                "ISIN                        IE00B3RBWM25",
                "Stück                       6,09607",
                "Kurs                        EUR 164,04",
                "Fremdspesen                 EUR 2,50",
                "Gesamtbetrag                EUR 1.000,00");
    }

    /** Dieselbe Bauart wie die übrigen Testfassungen – Spalten aus Leerzeichen. */
    private static PdfText text(String... lines) {
        return StatementFixtures.of(lines);
    }

    /** Eine Vorlage mit Gesamtbetrag und wahlweise einer Gebührenregel. */
    private static StatementTemplate vorlage(String action, boolean mitGebuehrenregel, boolean summe,
                                             long festeGebuehr, boolean imGesamtbetrag) {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single("Gesamtbetrag", AnchorRule.Direction.SAME_LINE));
        if (mitGebuehrenregel) {
            rules.put(StatementTemplate.Field.FEE, new AnchorRule(
                    Arrays.asList("Fremdspesen"), AnchorRule.Direction.SAME_LINE, summe, ""));
        }
        return new StatementTemplate(action, rules, festeGebuehr, "Bankgebühren", imGesamtbetrag);
    }

    // ---- Was die Gebühr wird ----

    @Test
    public void ohneGebuehrenzeileGiltDerFesteWert() {
        StatementTemplate.Extraction e =
                vorlage("buy", false, false, 99L, false).apply(ohneGebuehr());
        assertEquals(Long.valueOf(99L), e.feeCents);
        assertEquals("Bankgebühren", e.feeCategory);
    }

    /** Die Regel sucht und findet nichts: dann greift der feste Wert genauso. */
    @Test
    public void eineErgebnisloseRegelLaesstDenFestenWertGelten() {
        StatementTemplate.Extraction e =
                vorlage("buy", true, false, 99L, false).apply(ohneGebuehr());
        assertEquals(Long.valueOf(99L), e.feeCents);
    }

    @Test
    public void mitZusammenzaehlenKommtDerFesteWertHinzu() {
        StatementTemplate.Extraction e =
                vorlage("buy", true, true, 99L, false).apply(mitGebuehr());
        assertEquals("250 gefunden + 99 fest", Long.valueOf(349L), e.feeCents);
    }

    @Test
    public void ohneZusammenzaehlenGewinntDerGefundeneWert() {
        StatementTemplate.Extraction e =
                vorlage("buy", true, false, 99L, false).apply(mitGebuehr());
        assertEquals(Long.valueOf(250L), e.feeCents);
        assertEquals("die Kategorie gehört zur festen Gebühr, die hier nicht greift", "", e.feeCategory);
    }

    // ---- Was der Gesamtbetrag wird ----

    @Test
    public void nichtEnthaltenErhoehtDenGesamtbetragBeimKauf() {
        StatementTemplate.Extraction e =
                vorlage("buy", false, false, 99L, false).apply(ohneGebuehr());
        assertEquals("100000 vom Beleg + 99 Gebühr", Long.valueOf(100_099L), e.netCents);
    }

    @Test
    public void nichtEnthaltenVermindertDieGutschriftBeimVerkauf() {
        StatementTemplate.Extraction e =
                vorlage("sell", false, false, 99L, false).apply(ohneGebuehr());
        assertEquals(Long.valueOf(99_901L), e.netCents);
    }

    @Test
    public void enthaltenLaesstDenGesamtbetragStehen() {
        StatementTemplate.Extraction e =
                vorlage("buy", false, false, 99L, true).apply(ohneGebuehr());
        assertEquals(Long.valueOf(100_000L), e.netCents);
    }

    /** Was nicht angesetzt wurde, darf auch den Gesamtbetrag nicht anrühren. */
    @Test
    public void einNichtAngesetzterFesterWertLaesstDenGesamtbetragStehen() {
        StatementTemplate.Extraction e =
                vorlage("buy", true, false, 99L, false).apply(mitGebuehr());
        assertEquals(Long.valueOf(100_000L), e.netCents);
    }

    // ---- Nichts eingetragen: alles wie bisher ----

    @Test
    public void ohneFestenWertBleibtAllesWieBisher() {
        StatementTemplate.Extraction ohne =
                vorlage("buy", true, false, 0L, false).apply(mitGebuehr());
        assertEquals(Long.valueOf(250L), ohne.feeCents);
        assertEquals(Long.valueOf(100_000L), ohne.netCents);
        assertEquals("", ohne.feeCategory);
    }

    @Test
    public void ohneFestenWertUndOhneRegelBleibtDieGebuehrLeer() {
        StatementTemplate.Extraction e =
                vorlage("buy", false, false, 0L, false).apply(ohneGebuehr());
        assertNull(e.feeCents);
    }

    /**
     * Bei einer Dividende bedeutet «Regel gesucht, nichts gefunden» weiterhin 0 und nicht «unbekannt» —
     * sonst erfände der Steuersatz aus den Einstellungen eine Steuer, die es nicht gab.
     */
    @Test
    public void beiDerDividendeBleibtDieNullErhalten() {
        StatementTemplate.Extraction e =
                vorlage("dividend", true, false, 0L, false).apply(ohneGebuehr());
        assertEquals(Long.valueOf(0L), e.feeCents);
    }

    // ---- Die feste Gebühr überlebt das Lernen ----

    @Test
    public void einLernvorgangVerliertDenFestenWertNicht() {
        StatementTemplate vonHand = vorlage("buy", false, false, 99L, false);
        StatementTemplate gelernt = vorlage("buy", true, false, 0L, false);

        assertEquals(99L, gelernt.mergedOver(vonHand).fixedFeeCents);
        assertEquals("Bankgebühren", gelernt.mergedOver(vonHand).fixedFeeCategory);
        assertEquals(99L, gelernt.appendedTo(vonHand, PdfText.fromLines("")).fixedFeeCents);
    }

    // ---- Aus der Differenz auf eine feste Gebühr schliessen ----

    /**
     * Eine Abrechnung, auf der <b>weder</b> der abgebuchte Gesamtbetrag <b>noch</b> die Gebühr steht: die
     * Bank druckt nur den Betrag ohne ihre feste Ordergebühr. So rechnet Scalable Capital ab.
     */
    private static PdfText ohneGesamtbetrag() {
        return text(
                "Wertpapierabrechnung Kauf",
                "ISIN                        IE00B3RBWM25",
                "Stück                       6,09607",
                "Kurs                        EUR 164,04",
                "Kurswert                    EUR 1.000,00");
    }

    private static TemplateLearner.Known getippt(String action, long netCents, Long feeCents) {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = action;
        k.netCents = netCents;
        k.feeCents = feeCents;
        k.feeCategory = "Bankgebühren";
        return k;
    }

    @Test
    public void derBetragOhneGebuehrVerraetDieFesteGebuehr() {
        // Eingetippt: 1.000,99 abgebucht, davon 0,99 Gebühr. Im Beleg steht nur 1.000,00.
        StatementTemplate gelernt =
                TemplateLearner.learn(ohneGesamtbetrag(), getippt("buy", 100_099L, 99L));

        AnchorRule net = gelernt.rule(StatementTemplate.Field.NET);
        assertNotNull("die Zeile mit dem Betrag ohne Gebühr gehört gefunden", net);
        assertEquals("Kurswert", net.anchors.get(0));
        assertEquals(99L, gelernt.fixedFeeCents);
        assertEquals("Bankgebühren", gelernt.fixedFeeCategory);
        assertFalse("der ausgedruckte Betrag enthält sie ja gerade nicht", gelernt.fixedFeeInTotal);
    }

    /** Beim Verkauf mindert die Gebühr die Gutschrift – der ausgedruckte Betrag ist der höhere. */
    @Test
    public void beimVerkaufLiegtDerAusgedruckteBetragHoeher() {
        StatementTemplate gelernt =
                TemplateLearner.learn(ohneGesamtbetrag(), getippt("sell", 99_901L, 99L));

        assertNotNull(gelernt.rule(StatementTemplate.Field.NET));
        assertEquals(99L, gelernt.fixedFeeCents);
    }

    /** Steht der Gesamtbetrag im Beleg, gibt es nichts zu folgern. */
    @Test
    public void beiGefundenemGesamtbetragWirdNichtsGefolgert() {
        StatementTemplate gelernt =
                TemplateLearner.learn(ohneGebuehr(), getippt("buy", 100_000L, 99L));

        assertNotNull(gelernt.rule(StatementTemplate.Field.NET));
        assertEquals("nichts zu folgern", 0L, gelernt.fixedFeeCents);
    }

    /** Steht die Gebühr im Beleg, ist sie keine feste – dann fehlt nur der Gesamtbetrag. */
    @Test
    public void beiGefundenerGebuehrWirdNichtsGefolgert() {
        StatementTemplate gelernt =
                TemplateLearner.learn(mitGebuehr(), getippt("buy", 999_999L, 250L));

        assertEquals(0L, gelernt.fixedFeeCents);
    }

    /** Geht die Rechnung nicht auf, bleibt es beim Nichtfinden – geraten wird nichts. */
    @Test
    public void ohnePassendeDifferenzEntstehtKeineRegel() {
        StatementTemplate gelernt =
                TemplateLearner.learn(ohneGesamtbetrag(), getippt("buy", 100_099L, 55L));

        assertNull(gelernt.rule(StatementTemplate.Field.NET));
        assertEquals(0L, gelernt.fixedFeeCents);
    }

    /**
     * Bei einer Dividende ist die «Gebühr» die Steuer, und Brutto minus Steuer ergibt das Netto — die
     * Rechnung träfe regelmäßig die Bruttozeile und erfände eine Ordergebühr, die es nicht gibt.
     */
    @Test
    public void beiDerDividendeWirdNichtsGefolgert() {
        StatementTemplate gelernt =
                TemplateLearner.learn(ohneGesamtbetrag(), getippt("dividend", 100_099L, 99L));

        assertEquals(0L, gelernt.fixedFeeCents);
    }

    /** Sonst käme die Rückfrage «Für diese Bank merken?» nach jedem Speichern erneut. */
    @Test
    public void eineAndereFesteGebuehrIstEineAndereVorlage() {
        assertTrue(vorlage("buy", false, false, 99L, false)
                .sameAs(vorlage("buy", false, false, 99L, false)));
        assertTrue(!vorlage("buy", false, false, 99L, false)
                .sameAs(vorlage("buy", false, false, 250L, false)));
        assertTrue(!vorlage("buy", false, false, 99L, false)
                .sameAs(vorlage("buy", false, false, 99L, true)));
    }
}
