package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die Werteliste, die die Regelseite beim Tipp aufs Beschriftungsfeld vorlegt.
 *
 * <p>Ihre einzige Zusicherung ist zugleich ihr ganzer Zweck: was darin steht, findet die fertige Regel
 * hinterher wieder. Eine Liste, die aus eigener Anschauung Zahlen sammelte, könnte das nicht — sie böte
 * Werte an, die mit den eingestellten Angaben gar nicht erreichbar sind.</p>
 */
public class StatementScanValuesTest {

    private static List<StatementScan.ValueCandidate> gleicheZeile(PdfText text) {
        return StatementScan.values(text, AnchorRule.Direction.SAME_LINE, 0,
                AnchorRule.Position.LAST, 1, "");
    }

    private static StatementScan.ValueCandidate mit(List<StatementScan.ValueCandidate> found,
                                                    String label) {
        for (StatementScan.ValueCandidate c : found) {
            if (c.label.equalsIgnoreCase(label)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void beschriftungUndWertJeZeile() {
        List<StatementScan.ValueCandidate> found = gleicheZeile(StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "Kurswert EUR 1.100,00",
                "Provision EUR 4,90",
                "Endbetrag zu Ihren Lasten EUR 1.104,90"));
        assertNotNull(mit(found, "Kurswert"));
        assertEquals(1100.00, mit(found, "Kurswert").value, 0.001);
        assertEquals(4.90, mit(found, "Provision").value, 0.001);
        assertEquals(1104.90, mit(found, "Endbetrag zu Ihren Lasten").value, 0.001);
    }

    @Test
    public void jederKandidatIstAlsRegelWiederAuffindbar() {
        PdfText text = StatementFixtures.of(
                "Kurswert EUR 1.100,00",
                "Provision EUR 4,90",
                "Endbetrag zu Ihren Lasten EUR 1.104,90");
        for (StatementScan.ValueCandidate c : gleicheZeile(text)) {
            AnchorRule rule = new AnchorRule(Collections.singletonList(c.label),
                    AnchorRule.Direction.SAME_LINE, false);
            assertEquals(c.label, c.value, rule.read(text), 0.001);
        }
    }

    @Test
    public void zweiteZahlVonRechtsWirdBeruecksichtigt() {
        // Tabellenzeile: rechts der Kurs, davor die Stückzahl.
        PdfText text = StatementFixtures.of("Stück Kurs Wert", "Kauf 5 74,33 371,65");
        List<StatementScan.ValueCandidate> zweite = StatementScan.values(text,
                AnchorRule.Direction.SAME_LINE, 0, AnchorRule.Position.LAST, 2, "");
        assertEquals(74.33, mit(zweite, "Kauf").value, 0.001);
        List<StatementScan.ValueCandidate> letzte = gleicheZeile(text);
        assertEquals(371.65, mit(letzte, "Kauf").value, 0.001);
    }

    @Test
    public void darunterLiestDieZeileTiefer() {
        PdfText text = StatementFixtures.of("Nominale Bezeichnung", "EUR 2.000,00 Metalcorp");
        List<StatementScan.ValueCandidate> found = StatementScan.values(text,
                AnchorRule.Direction.LINE_BELOW, 1, AnchorRule.Position.FIRST, 1, "");
        assertEquals(2000.00, mit(found, "Nominale Bezeichnung").value, 0.001);
    }

    @Test
    public void fremdeWaehrungFaelltHeraus() {
        PdfText text = StatementFixtures.of("Brutto USD 1.053,47", "Umrechnung EUR 906,99");
        List<StatementScan.ValueCandidate> nurEuro = StatementScan.values(text,
                AnchorRule.Direction.SAME_LINE, 0, AnchorRule.Position.LAST, 1, "EUR");
        assertEquals(null, mit(nurEuro, "Brutto"));
        assertEquals(906.99, mit(nurEuro, "Umrechnung").value, 0.001);
    }

    @Test
    public void gleicheBeschriftungNurEinmal() {
        PdfText text = StatementFixtures.of(
                "Kapitalertragsteuer EUR 36,73",
                "Kapitalertragsteuer EUR 36,73");
        int wieOft = 0;
        for (StatementScan.ValueCandidate c : gleicheZeile(text)) {
            if (c.label.equalsIgnoreCase("Kapitalertragsteuer")) {
                wieOft++;
            }
        }
        assertEquals(1, wieOft);
    }

    @Test
    public void zeilenOhneBeschriftungBleibenDraussen() {
        List<StatementScan.ValueCandidate> found = gleicheZeile(StatementFixtures.of(
                "1.234,56",
                "Kurswert EUR 1.100,00"));
        assertEquals(1, found.size());
        assertTrue(found.get(0).label.equalsIgnoreCase("Kurswert"));
    }
}
