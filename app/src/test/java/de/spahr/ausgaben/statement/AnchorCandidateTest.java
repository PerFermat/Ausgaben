package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.SecurityAmounts;

/**
 * Die Auswahl der Beschriftung: was die Maske dem Nutzer vorlegt, wenn er ein Wertfeld verlässt.
 *
 * <p>Der Lerner muss sonst raten, und an einer Tabellenzeile ist das nicht zu entscheiden: „STK 86
 * Vanguard FTSE All-World U.ETF EUR 116,20" unter der Überschrift „Nominale … Kurs" gibt für den
 * Kurs drei Beschriftungen her, und welche die Bank im nächsten Beleg behält, steht nicht im
 * Dokument. Also legt die App sie vor — wie sie es beim Datum längst tut.</p>
 */
public class AnchorCandidateTest {

    private static final double MONEY = 0.005;

    private static List<AnchorRule> fuer(PdfText text, double wert) {
        return TemplateLearner.kandidaten(text, wert, MONEY, false);
    }

    /** Die Beschriftungen der Kandidaten, kleingeschrieben — für die Zusicherungen unten. */
    private static List<String> anker(List<AnchorRule> kandidaten) {
        List<String> out = new java.util.ArrayList<>();
        for (AnchorRule rule : kandidaten) {
            out.add(rule.anchors.get(0).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    private static boolean enthaelt(List<AnchorRule> kandidaten, String anker) {
        for (String a : anker(kandidaten)) {
            if (a.equals(anker.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Die Zusicherung, die eine Auswahlliste tragen muss: <b>jeder</b> angebotene Weg liest den Wert
     * hinterher wieder. Eine Liste, die auch nur einen Weg anbietet, der etwas anderes liest, wäre
     * schlimmer als keine — der Nutzer hat ihn dann ja ausdrücklich bestätigt.
     */
    @Test
    public void jederAngeboteneWegLiestSeinenWertWieder() {
        PdfText text = StatementFixtures.scalableKauf();

        for (AnchorRule rule : fuer(text, 116.20)) {
            Double gelesen = rule.read(text);
            assertEquals("Kandidat " + rule.anchors + " liest etwas anderes",
                    116.20, gelesen == null ? Double.NaN : gelesen, MONEY);
        }
    }

    /** Beim Kauf sind es genau die drei Wege, um die es geht — Überschrift, Stückangabe, Fondsname. */
    @Test
    public void derKursBietetUeberschriftUndZeileAn() {
        List<AnchorRule> kandidaten = fuer(StatementFixtures.scalableKauf(), 116.20);

        assertTrue("die Spaltenüberschrift fehlt: " + anker(kandidaten),
                enthaelt(kandidaten, "Kurs"));
        assertTrue("die Stückangabe daneben fehlt: " + anker(kandidaten),
                enthaelt(kandidaten, "STK"));
    }

    /**
     * Und beim Verkauf ist „STK" <b>nicht</b> dabei: derselbe Anker steht dort auf Seite 2 noch einmal,
     * und beim Lesen gewinnt die unterste Fundstelle — 4.859,11 statt 126,933. Was sich nicht selbst
     * liest, wird gar nicht erst angeboten.
     */
    @Test
    public void wasSichNichtSelbstLiestWirdNichtAngeboten() {
        List<AnchorRule> kandidaten = fuer(StatementFixtures.scalableVerkaufZweiSeitig(), 126.933);

        assertFalse("STK liest hier 4.859,11 und darf nicht in der Auswahl stehen: "
                + anker(kandidaten), enthaelt(kandidaten, "STK"));
        assertTrue("die Spaltenüberschrift fehlt: " + anker(kandidaten),
                enthaelt(kandidaten, "Kurs"));
    }

    /** Steht der Wert nicht im Dokument, gibt es nichts zu fragen — und keine geratene Regel. */
    @Test
    public void einNichtVorkommenderWertGibtKeineKandidaten() {
        assertTrue(fuer(StatementFixtures.scalableKauf(), 4711.23).isEmpty());
    }

    /**
     * Die Wahl setzt sich gegen den eigenen Vorschlag durch — sonst wäre die Auswahl eine Anzeige und
     * keine Entscheidung.
     */
    @Test
    public void diegewaehlteRegelSchlaegtDenEigenenVorschlag() {
        PdfText text = StatementFixtures.scalableKauf();
        AnchorRule ueberschrift = null;
        for (AnchorRule rule : fuer(text, 116.20)) {
            if (rule.position == AnchorRule.Position.COLUMN) {
                ueberschrift = rule;
            }
        }
        assertTrue("keine Spaltenregel unter den Kandidaten", ueberschrift != null);

        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = "buy";
        known.shares = 86.0;
        known.price = 116.20;
        known.netCents = 999320L;
        known.chosenRules.put(StatementTemplate.Field.PRICE, ueberschrift);

        StatementTemplate t = TemplateLearner.learn(text, known);

        assertEquals(ueberschrift, t.rule(StatementTemplate.Field.PRICE));
        assertEquals(116.20, t.apply(text).price, MONEY);
    }

    /**
     * Die Stückzahl geht denselben Weg. Sie ist der Wert, bei dem das Raten am teuersten ist: eine
     * glatte Zahl steht in einer Abrechnung schnell mehrfach.
     */
    @Test
    public void auchDieStueckzahlLaesstSichWaehlen() {
        PdfText text = StatementFixtures.scalableKauf();
        List<AnchorRule> kandidaten =
                TemplateLearner.kandidaten(text, 86.0, SecurityAmounts.SHARE_EPSILON, false);

        assertFalse("für die Stückzahl wird gar nichts angeboten", kandidaten.isEmpty());
        for (AnchorRule rule : kandidaten) {
            Double gelesen = rule.read(text);
            assertEquals("Kandidat " + rule.anchors + " liest etwas anderes",
                    86.0, gelesen == null ? Double.NaN : gelesen, SecurityAmounts.SHARE_EPSILON);
        }
    }
}
