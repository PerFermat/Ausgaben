package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Werte, die unter einer <b>Spaltenüberschrift</b> stehen statt hinter einer Beschriftung.
 *
 * <p>Der Fall, an dem die Ankerlogik lange scheiterte: die Datenzeile beginnt mit einer Zahl und trägt
 * deshalb gar keine Beschriftung, und zwischen Überschrift und Daten steht noch eine zweite Kopfzeile.
 * Weder «in derselben Zeile» noch «genau eine Zeile darunter» erreicht den Wert.</p>
 *
 * <p>Dazu die zweite Hälfte desselben Problems: eine Tabellenzeile trägt oft <b>mehrere</b> Daten —
 * vorn den Buchungstag, daneben die Wertstellung. Gebucht gehört die zweite.</p>
 */
public class TableStatementTest {

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    private static PdfText beleg() {
        return StatementFixtures.tabellenDividende();
    }

    // ---- Lesen ----

    /** Die von Hand angelegte Regel, wie ein Nutzer sie auf der Regelseite einträgt. */
    @Test
    public void eineHandRegelErreichtDieZeileUnterDerUeberschrift() {
        AnchorRule regel = AnchorRule.single("Wertstellung", AnchorRule.Direction.LINE_BELOW, "",
                AnchorRule.Position.FIRST, 2);
        assertEquals("die Wertstellung, nicht der Buchungstag",
                tag(2026, 7, 1), regel.readDate(beleg()));
    }

    /**
     * Ohne Stellenangabe gilt beim Datum weiterhin <b>das erste</b> hinter der Beschriftung — auch wenn
     * die Vorgabe wörtlich «die letzte» heisst.
     *
     * <p>Das ist Rücksicht auf den Bestand: gelernte Regeln tragen diese Vorgabe, ohne dass jemand sie
     * gewählt hätte. Eine Zeile wie «Für 01.07.2025 - 30.06.2026» läse sonst über Nacht das Enddatum
     * statt des Anfangs, und niemand käme darauf, warum.</p>
     */
    @Test
    public void ohneStellenangabeGiltWeiterhinDasErsteDatum() {
        AnchorRule regel = AnchorRule.single("Wertstellung", AnchorRule.Direction.LINE_BELOW);
        assertEquals(tag(2026, 6, 30), regel.readDate(beleg()));

        AnchorRule ausdruecklich = AnchorRule.single("Wertstellung", AnchorRule.Direction.LINE_BELOW,
                "", AnchorRule.Position.LAST, 1);
        assertEquals("dieselbe Vorgabe, ausdrücklich gesetzt – dieselbe Bedeutung",
                tag(2026, 6, 30), ausdruecklich.readDate(beleg()));
    }

    /** Wer die letzte Angabe will, sagt es über «von links» oder eine Stelle grösser eins. */
    @Test
    public void diezweiteVonLinksIstDieWertstellung() {
        AnchorRule regel = AnchorRule.single("Wertstellung", AnchorRule.Direction.LINE_BELOW, "",
                AnchorRule.Position.FIRST, 2);
        assertEquals(tag(2026, 7, 1), regel.readDate(beleg()));
    }

    /**
     * «Genau eine Zeile darunter» landet auf der zweiten Kopfzeile und findet nichts. Das ist kein
     * Mangel, sondern der Zweck der Angabe: sie ist die feste Fassung für den Fall, dass die suchende
     * an einer Zwischenzeile hängenbleibt.
     */
    @Test
    public void genauEineZeileDarunterTrifftDieKopfzeile() {
        AnchorRule regel = new AnchorRule(java.util.Collections.singletonList("Wertstellung"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.LAST, 1, 1);
        assertEquals(-1, regel.readDate(beleg()));
    }

    /** Genau zwei Zeilen darunter ist die Datenzeile — Abstand und Stelle greifen zusammen. */
    @Test
    public void genauZweiZeilenDarunterTrifftDieDatenzeile() {
        AnchorRule regel = new AnchorRule(java.util.Collections.singletonList("Wertstellung"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.FIRST, 2, 2);
        assertEquals(tag(2026, 7, 1), regel.readDate(beleg()));
    }

    /** Auch Beträge stehen so: der Gesamtwert als letzte Zahl der Datenzeile. */
    @Test
    public void auchEinBetragIstUnterDerUeberschriftErreichbar() {
        AnchorRule regel = AnchorRule.single("Wertstellung", AnchorRule.Direction.LINE_BELOW);
        assertEquals(Long.valueOf(64836L), regel.readCents(beleg()));
    }

    /** Die suchende Fassung darf nicht beliebig weit greifen. */
    @Test
    public void weiterAlsDreiZeilenWirdNichtGesucht() {
        AnchorRule regel = AnchorRule.single("Kontobewegung", AnchorRule.Direction.LINE_BELOW, "",
                AnchorRule.Position.LAST, 1);
        // Von «Kontobewegung» sind es drei Zeilen bis zu den Daten – gerade noch in Reichweite.
        assertEquals(tag(2026, 6, 30), regel.readDate(beleg()));

        AnchorRule zuWeit = AnchorRule.single("Ex Tag", AnchorRule.Direction.LINE_BELOW, "",
                AnchorRule.Position.LAST, 1);
        // «Ex Tag» steht vier Zeilen über den Daten; die eigene Zeile zählt nicht mit.
        assertEquals("weiter als drei Zeilen darf nicht gegriffen werden",
                -1, zuWeit.readDate(beleg()));
    }

    // ---- Lernen ----

    /**
     * Der eigentliche Anspruch: die App soll diesen Aufbau von selbst lernen, damit die nächste
     * Abrechnung derselben Bank ohne Handarbeit auskommt.
     */
    @Test
    public void derLernerFindetDieUeberschriftUeberDerDatenzeile() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.netCents = 52210L;
        k.dateMillis = tag(2026, 7, 1);

        StatementTemplate gelernt = TemplateLearner.learn(beleg(), k);
        AnchorRule datum = gelernt.rule(StatementTemplate.Field.DATE);
        assertNotNull("ohne Datumsregel ist die Vorlage die Handarbeit nicht wert", datum);
        assertEquals(AnchorRule.Direction.LINE_BELOW, datum.direction);
        assertEquals("und sie muss dasselbe wieder herauslesen",
                tag(2026, 7, 1), datum.readDate(beleg()));
    }

    /** Der Gesamtbetrag steht hinter einer gewöhnlichen Beschriftung und bleibt einfach. */
    @Test
    public void derGesamtbetragBleibtEineGewoehnlicheRegel() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.netCents = 52210L;

        AnchorRule netto = TemplateLearner.learn(beleg(), k).rule(StatementTemplate.Field.NET);
        assertNotNull(netto);
        assertEquals("Gesamtbetrag", netto.anchors.get(0));
        assertEquals(AnchorRule.Direction.SAME_LINE, netto.direction);
    }

    /** Die Steuer steht doppelt im Beleg – gelernt gehört die Zeile, die sie ausweist. */
    @Test
    public void dieSteuerWirdUeberIhreEigeneZeileGefunden() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.netCents = 52210L;
        k.feeCents = 12626L;

        AnchorRule steuer = TemplateLearner.learn(beleg(), k).rule(StatementTemplate.Field.FEE);
        assertNotNull(steuer);
        assertEquals(Long.valueOf(12626L), steuer.readCents(beleg()));
    }

    /** Ein Wert, den es im Beleg nicht gibt, ergibt weiterhin keine Regel — geraten wird nichts. */
    @Test
    public void einFremderWertErgibtKeineRegel() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.netCents = 99999L;
        assertNull(TemplateLearner.learn(beleg(), k).rule(StatementTemplate.Field.NET));
    }
}
