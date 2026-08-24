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

    /**
     * Der zurückgerechnete Kurs weicht in der letzten Stelle ab: 1.000,00 ÷ 6,09607 ergibt 164,0401,
     * im Dokument steht 164,04. Ein halber Cent Toleranz lässt die Zeile trotzdem finden — sonst käme
     * die Vorlage nie zu einer Kursregel, und bei jedem Einlesen käme die Rückfrage erneut.
     */
    @Test
    public void einZurückgerechneterKursFindetSeineZeileTrotzdem() {
        TemplateLearner.Known k = kauf();
        k.price = 1000.00 / 6.09607;   // 164,0401…
        AnchorRule price = TemplateLearner.learn(StatementFixtures.ingKauf(), k)
                .rule(StatementTemplate.Field.PRICE);
        assertNotNull("sollte die Kurszeile finden", price);
        assertEquals("Kurs", price.anchors.get(0));
        // Gelesen wird danach der Wert der Abrechnung, nicht der gerechnete.
        assertEquals(Double.valueOf(164.04), price.read(StatementFixtures.ingKauf()));
    }

    /** Ein Kurs, der um mehr als einen halben Cent abweicht, ist ein anderer Wert. */
    @Test
    public void einDeutlichAbweichenderKursFindetNichts() {
        TemplateLearner.Known k = kauf();
        k.price = 164.05;
        assertNull(TemplateLearner.learn(StatementFixtures.ingKauf(), k)
                .rule(StatementTemplate.Field.PRICE));
    }

    /** Bei Stückzahlen bleibt es eng: 0,005 Stück wären bei einem Sparplan ein echter Unterschied. */
    @Test
    public void beiStückzahlenGiltKeineToleranz() {
        TemplateLearner.Known k = kauf();
        k.shares = 6.09;
        assertNull(TemplateLearner.learn(StatementFixtures.ingKauf(), k)
                .rule(StatementTemplate.Field.SHARES));
    }

    // ---- Währung ----

    @Test
    public void nurDieBuchungsbetraegeBindenSichAnEineWährung() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        // Gesamtsumme geht als Buchung aufs Konto – immer Kontowährung.
        assertEquals("EUR", t.rule(StatementTemplate.Field.NET).currency);
        // Der Stückpreis ist die Notierung des Papiers: bei einem Dollar-Papier steht dort USD. Wäre er
        // an EUR gebunden, ginge nach dem Lernen an einem Euro-Papier kein Dollar-Papier mehr.
        assertEquals("", t.rule(StatementTemplate.Field.PRICE).currency);
        assertEquals("", t.rule(StatementTemplate.Field.SHARES).currency);
    }

    /**
     * Der Fall, um den es geht: dieselbe Bank, einmal ein Dollar-Papier mit Umrechnungszeile, einmal ein
     * Euro-Papier ohne. Eine an einem Euro-Papier gelernte Vorlage muss beide lesen können.
     */
    @Test
    public void dieselbeVorlageLiestDollarUndEuroPapiere() {
        TemplateLearner.Known k = kauf();
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), k);

        PdfText dollarPapier = StatementFixtures.of(
                "Wertpapierabrechnung        Kauf",
                "ISIN (WKN)                  IE00B3RBWM25 (A1JX52)",
                "Nominale                    Stück            10,00000",
                "Kurs                        USD                85,00",
                "Ausführungstag / -zeit      17.08.2026 um 09:04:58 Uhr",
                "Kurswert                    USD               850,00",
                "Umg. z. Dev.-Kurs (1,1615)  EUR               731,81",
                "Endbetrag zu Ihren Lasten   EUR               731,81");
        assertTrue("Vorlage sollte passen", t.matches(dollarPapier));
        StatementTemplate.Extraction e = t.apply(dollarPapier);
        // Die Gesamtsumme ist der Eurobetrag – nicht die 850,00 USD.
        assertEquals(Long.valueOf(73181L), e.netCents);
        assertEquals(10.0, e.shares, 1e-9);
        // Der Kurs ist die Notierung in Dollar; er wird gelesen, statt an EUR zu scheitern.
        assertEquals(85.0, e.price, 1e-9);
    }

    /**
     * Der eigentliche Zweck: dieselbe Abrechnung führt Beträge in mehreren Währungen. Eine auf EUR
     * gelernte Regel darf den Dollarbetrag nicht lesen, auch wenn die Beschriftung passt.
     */
    @Test
    public void eineEuroRegelLiestKeinenDollarbetrag() {
        PdfText t = StatementFixtures.of(
                "Brutto                        USD           1.053,47",
                "Zwischensumme                 USD           1.053,47",
                "Umg. z. Dev.-Kurs (1,161497)  EUR             906,99");
        AnchorRule euro = AnchorRule.single("Zwischensumme", AnchorRule.Direction.SAME_LINE, "EUR");
        assertNull("USD-Zeile darf nicht gelesen werden", euro.read(t));

        AnchorRule dollar = AnchorRule.single("Zwischensumme", AnchorRule.Direction.SAME_LINE, "USD");
        assertEquals(Double.valueOf(1053.47), dollar.read(t));
    }

    @Test
    public void ohneGelernteWährungBleibtEsWieBisher() {
        PdfText t = StatementFixtures.of("Zwischensumme   USD   1.053,47");
        assertEquals(Double.valueOf(1053.47),
                AnchorRule.single("Zwischensumme", AnchorRule.Direction.SAME_LINE).read(t));
    }

    @Test
    public void dieSteuerSummeBindetSichEbenfallsAnDieKontowährung() {
        AnchorRule fee = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende())
                .rule(StatementTemplate.Field.FEE);
        assertEquals("EUR", fee.currency);
        assertEquals(Long.valueOf(16746L), fee.readCents(StatementFixtures.ingDividende()));
    }

    /** Währungskennzeichen erkennen: dreistellige Großbuchstaben und die gängigen Symbole. */
    @Test
    public void währungWirdInDerZeileErkannt() {
        assertEquals("EUR", AnchorRule.currencyOf("Kurs EUR 164,04"));
        assertEquals("USD", AnchorRule.currencyOf("Brutto USD 1.053,47"));
        assertEquals("€", AnchorRule.currencyOf("Betrag 12,50 €"));
        assertEquals("", AnchorRule.currencyOf("Nominale Stück 6,09607"));
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
