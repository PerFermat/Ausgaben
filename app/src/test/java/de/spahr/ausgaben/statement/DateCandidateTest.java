package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die Auswahl legt <b>beide Lesarten</b> vor, wo es beide gibt.
 *
 * <pre>
 *         Wertstellung
 * zum 01.07.2026
 * </pre>
 *
 * <p>Dasselbe Datum ist hier auf zwei Arten zu erreichen: über die Überschrift <i>darüber</i> und über
 * die Beschriftung <i>daneben</i>. Welche die haltbarere ist, weiß die App nicht — ob die Bank im
 * nächsten Beleg die Tabelle behält oder die Zeile umbaut, entscheidet sie und nicht wir. Also werden
 * beide angeboten, und die Wahl des Nutzers bringt ihre Leseart gleich mit.</p>
 */
public class DateCandidateTest {

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    /** Überschrift darüber, Beschriftung daneben — und zwei Daten in der Zeile, also eine Tabelle. */
    private static PdfText beleg() {
        return StatementFixtures.of(
                "Ertragsgutschrift",
                "Buchung        Wertstellung",
                "zum 30.06.2026 01.07.2026",
                "Gesamt         648,36");
    }

    private static StatementScan.DateCandidate kandidat(String label) {
        for (StatementScan.DateCandidate c : StatementScan.dates(beleg())) {
            if (label.equals(c.label)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void dieUeberschriftDarueberStehtZurWahl() {
        StatementScan.DateCandidate c = kandidat("Wertstellung");
        assertNotNull("die Spaltenüberschrift fehlte", c);
        assertEquals(tag(2026, 7, 1), c.millis);
        assertEquals(AnchorRule.Position.COLUMN, c.rule.position);
        assertEquals(AnchorRule.Direction.LINE_BELOW, c.rule.direction);
    }

    @Test
    public void dieBeschriftungDanebenAuch() {
        StatementScan.DateCandidate c = kandidat("zum");
        assertNotNull("die Beschriftung der Zeile fehlte", c);
        assertEquals(tag(2026, 6, 30), c.millis);
        assertEquals(AnchorRule.Direction.SAME_LINE, c.rule.direction);
    }

    /** Beide lesen wirklich, was in der Liste steht — sonst wäre die Auswahl ein Versprechen ins Blaue. */
    @Test
    public void jederEintragLiestWasDraufsteht() {
        for (StatementScan.DateCandidate c : StatementScan.dates(beleg())) {
            assertEquals("Eintrag „" + c.label + "“", c.millis, c.rule.readDate(beleg()));
        }
    }

    /**
     * Und die Grenze: über einem einzelnen Datum ohne Tabelle wird keine „Überschrift" erfunden. Sonst
     * stünde über dem Briefdatum am rechten Rand die Anschrift und böte sich als Beschriftung an.
     */
    @Test
    public void ohneTabelleKeineErfundeneUeberschrift() {
        PdfText brief = StatementFixtures.of(
                "Musterstrasse 4",
                "70000 Musterstadt                          Datum 30.06.2026");
        for (StatementScan.DateCandidate c : StatementScan.dates(brief)) {
            assertEquals("nur die Beschriftung der Zeile selbst, nicht die Zeile darüber",
                    AnchorRule.Direction.SAME_LINE, c.rule.direction);
        }
        assertNull(kandidatIn(brief, "Musterstrasse"));
    }

    private static StatementScan.DateCandidate kandidatIn(PdfText text, String label) {
        for (StatementScan.DateCandidate c : StatementScan.dates(text)) {
            if (label.equals(c.label)) {
                return c;
            }
        }
        return null;
    }
}
