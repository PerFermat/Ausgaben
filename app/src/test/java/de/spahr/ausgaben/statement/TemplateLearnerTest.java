package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Der Kern der PDF-Auslese: aus einer einmal von Hand erfassten Abrechnung ableiten, wo die Werte stehen,
 * und dieselbe Auslese danach auf die nächste Abrechnung derselben Bank anwenden.
 *
 * <p>Geprüft wird an den beiden echten ING-Dokumenten (siehe {@link StatementFixtures}) und an den
 * Fällen, die dabei schiefgehen können: mehrfach vorkommende Beträge, aufgeteilte Steuer, verschobene
 * Zeilen, fehlende Gebührenzeile.</p>
 */
public class TemplateLearnerTest {

    private static TemplateLearner.Known kauf() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 6.09607;
        k.price = 164.04;
        k.netCents = 100000L;      // Endbetrag zu Ihren Lasten
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        return k;
    }

    private static TemplateLearner.Known dividende() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.shares = 1839.80185;
        k.feeCents = 16746L;       // Kapitalertragsteuer 158,73 + Solidaritätszuschlag 8,73
        k.netCents = 73953L;       // Gesamtbetrag zu Ihren Gunsten
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        return k;
    }

    // ---- Lernen ----

    @Test
    public void ausDemKaufWerdenDieAnkerAbgeleitet() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        assertEquals("Endbetrag zu Ihren Lasten",
                t.rule(StatementTemplate.Field.NET).anchors.get(0));
        assertEquals("Kurs", t.rule(StatementTemplate.Field.PRICE).anchors.get(0));
        assertEquals("Nominale Stück", t.rule(StatementTemplate.Field.SHARES).anchors.get(0));
    }

    /**
     * 1.000,00 steht dreimal im Dokument (Kurswert, Zwischensumme, Endbetrag). Gelernt werden muss die
     * unterste Zeile — nur sie stimmt auch dann noch, wenn eine Gebühr dazukommt.
     */
    @Test
    public void beiMehrfachemBetragGewinntDieUntersteZeile() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        assertEquals("Endbetrag zu Ihren Lasten",
                t.rule(StatementTemplate.Field.NET).anchors.get(0));
    }

    /** „Kurs" darf nicht auf „Kurswert" anschlagen, sonst läse der Stückpreis den Gesamtbetrag. */
    @Test
    public void kursTrifftNichtDenKurswert() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        assertEquals(Double.valueOf(164.04),
                t.rule(StatementTemplate.Field.PRICE).read(StatementFixtures.ingKauf()));
    }

    /** Die Steuer steht auf zwei Zeilen; gelernt wird eine Summenregel über beide. */
    @Test
    public void aufgeteilteSteuerWirdAlsSummeGelernt() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende());
        AnchorRule fee = t.rule(StatementTemplate.Field.FEE);
        assertNotNull(fee);
        assertTrue("sollte summieren", fee.sum);
        assertEquals(2, fee.anchors.size());
        assertTrue(fee.anchors.contains("Kapitalertragsteuer"));
        assertTrue(fee.anchors.contains("Solidaritätszuschlag"));
    }

    @Test
    public void dasGebuchteDatumWirdAmAnkerGelernt() {
        // Die Abrechnung trägt vier Datumsangaben; gebucht ist der Ausführungstag.
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        AnchorRule date = t.rule(StatementTemplate.Field.DATE);
        assertNotNull(date);
        assertEquals("Ausführungstag / -zeit", date.anchors.get(0));
    }

    @Test
    public void nichtGefundeneWerteErgebenKeineRegel() {
        TemplateLearner.Known k = kauf();
        k.price = 999.99;   // steht nirgends im Dokument
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), k);
        assertNull(t.rule(StatementTemplate.Field.PRICE));
        // Die übrigen Regeln entstehen trotzdem.
        assertNotNull(t.rule(StatementTemplate.Field.NET));
    }

    @Test
    public void keinAnkerWirdZweimalVergeben() {
        // Stückzahl und Stückpreis wären beide 164,04 – der zweite darf den Anker nicht mitbenutzen.
        TemplateLearner.Known k = kauf();
        k.shares = 164.04;
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), k);
        AnchorRule price = t.rule(StatementTemplate.Field.PRICE);
        AnchorRule shares = t.rule(StatementTemplate.Field.SHARES);
        if (price != null && shares != null) {
            assertFalse(price.anchors.get(0).equals(shares.anchors.get(0)));
        }
    }

    /**
     * „Zahltag" und „Valuta" tragen in der Ertragsgutschrift dasselbe Datum. Ohne die ausdrückliche Wahl
     * des Nutzers gewänne die unterste Zeile — in der nächsten Abrechnung, wo die beiden auseinanderfallen,
     * wäre das die falsche.
     */
    @Test
    public void dieGewählteDatumsbeschriftungGewinnt() {
        TemplateLearner.Known k = dividende();
        k.dateAnchor = "Zahltag";
        AnchorRule date = TemplateLearner.learn(StatementFixtures.ingDividende(), k)
                .rule(StatementTemplate.Field.DATE);
        assertNotNull(date);
        assertEquals("Zahltag", date.anchors.get(0));
    }

    @Test
    public void ohneWahlGewinntWeiterhinDieUntersteZeile() {
        TemplateLearner.Known k = dividende();
        AnchorRule date = TemplateLearner.learn(StatementFixtures.ingDividende(), k)
                .rule(StatementTemplate.Field.DATE);
        assertNotNull(date);
        assertEquals("Valuta", date.anchors.get(0));
    }

    // ---- Anwenden ----

    @Test
    public void dieGelernteVorlageLiestDenKaufWiederAus() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingKauf());
        assertEquals(StatementScan.BUY, e.action);
        assertEquals("IE00B3RBWM25", e.isin);
        assertEquals(6.09607, e.shares, 1e-9);
        assertEquals(164.04, e.price, 1e-9);
        assertEquals(Long.valueOf(100000L), e.netCents);
        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026"), e.dateMillis);
    }

    @Test
    public void dieGelernteVorlageLiestDieDividendeWiederAus() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende());
        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingDividende());
        assertEquals("IE00B9CQXS71", e.isin);
        assertEquals(1839.80185, e.shares, 1e-9);
        assertEquals(Long.valueOf(16746L), e.feeCents);
        assertEquals(Long.valueOf(73953L), e.netCents);
        // Der Bruttobetrag wird nicht ausgelesen: im PDF steht er in USD. 739,53 + 167,46 = 906,99 EUR.
        assertEquals(90699L, e.netCents + e.feeCents);
    }

    /**
     * Der eigentliche Anspruch: eine <b>zweite</b> Abrechnung derselben Bank mit anderen Zahlen und
     * verschobenen Zeilen muss die Vorlage unverändert bedienen.
     */
    @Test
    public void dieVorlageTrägtAuchDieNächsteAbrechnung() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        PdfText zweite = StatementFixtures.of(
                "ING-DiBa AG · 60628 Frankfurt am Main",
                "Datum: 20.09.2026",
                "Wertpapierabrechnung        Kauf",
                "ISIN (WKN)                  IE00B3RBWM25 (A1JX52)",
                "Wertpapierbezeichnung       Vanguard FTSE All-World U.ETF",
                "                            Registered Shares USD Dis.oN",
                "                            noch eine Zeile Kleingedrucktes",
                "Nominale                    Stück            3,12345",
                "Kurs                        EUR               170,50",
                "Handelsplatz                Xetra",
                "Ausführungstag / -zeit      19.09.2026 um 10:11:12 Uhr",
                "Kurswert                    EUR               532,63",
                "Provision                   EUR                 4,90",
                "Zwischensumme               EUR               537,53",
                "Endbetrag zu Ihren Lasten   EUR               537,53");
        assertTrue("Vorlage sollte passen", t.matches(zweite));
        StatementTemplate.Extraction e = t.apply(zweite);
        assertEquals(3.12345, e.shares, 1e-9);
        assertEquals(170.50, e.price, 1e-9);
        assertEquals(Long.valueOf(53753L), e.netCents);
        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("19.09.2026"), e.dateMillis);
    }

    @Test
    public void eineFremdeAbrechnungPasstNichtZurVorlage() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        PdfText andereBank = StatementFixtures.of(
                "Musterbank AG",
                "Wertpapierkauf",
                "ISIN                        IE00B3RBWM25",
                "Anteile                     5,00000",
                "Preis je Anteil             100,00",
                "Belastung Ihres Kontos      500,00");
        assertFalse(t.matches(andereBank));
    }

    /** Die Dividenden-Vorlage darf nicht auf eine Kauf-Abrechnung passen. */
    @Test
    public void vorlagenDerBeidenArtenVerwechselnSichNicht() {
        StatementTemplate div = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende());
        assertFalse(div.matches(StatementFixtures.ingKauf()));
        StatementTemplate buy = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        assertFalse(buy.matches(StatementFixtures.ingDividende()));
    }

    // ---- Beschriftung ----

    @Test
    public void beschriftungEndetVorDerErstenZifferUndOhneWährung() {
        assertEquals("Endbetrag zu Ihren Lasten",
                TemplateLearner.labelOf("Endbetrag zu Ihren Lasten   EUR   1.000,00"));
        assertEquals("Kapitalertragsteuer",
                TemplateLearner.labelOf("Kapitalertragsteuer 25,00%   EUR   158,73"));
        assertEquals("Nominale", TemplateLearner.labelOf("Nominale   1.839,80185 Stück"));
        assertEquals("", TemplateLearner.labelOf("   1.000,00"));
    }

    @Test
    public void derWertUnterDerÜberschriftWirdAuchGefunden() {
        PdfText t = StatementFixtures.of(
                "Abrechnungsbetrag",
                "  1.234,56",
                "Sonstiges  0,00");
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.netCents = 123456L;
        AnchorRule net = TemplateLearner.learn(t, k).rule(StatementTemplate.Field.NET);
        assertNotNull(net);
        assertEquals("Abrechnungsbetrag", net.anchors.get(0));
        assertEquals(AnchorRule.Direction.LINE_BELOW, net.direction);
        assertEquals(Double.valueOf(1234.56), net.read(t));
    }
}
