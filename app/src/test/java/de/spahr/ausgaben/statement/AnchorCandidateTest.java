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

    /**
     * Regression: Wer übers Stift-Symbol eine andere Beschriftung wählt, muss sie auch gelernt bekommen.
     * Im Beleg steht die Stückzahl als „STK 86" unter der Spaltenüberschrift „Nominale" — der Lerner
     * schlägt von sich aus „STK" vor, wählt der Nutzer aber die Spalte, gilt seine Wahl.
     */
    @Test
    public void diePerHandGewaehlteBeschriftungWirdGelernt() {
        PdfText text = StatementFixtures.scalableKauf();
        AnchorRule spalte = null;
        for (AnchorRule rule : TemplateLearner.kandidaten(
                text, 86.0, SecurityAmounts.SHARE_EPSILON, false)) {
            if ("Nominale".equalsIgnoreCase(rule.anchors.get(0))) {
                spalte = rule;
                break;
            }
        }
        assertTrue("die Spaltenüberschrift wird gar nicht angeboten", spalte != null);

        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 86.0;
        k.chosenRules.put(StatementTemplate.Field.SHARES, spalte);
        StatementTemplate t = TemplateLearner.learn(text, k);

        assertEquals(spalte, t.rule(StatementTemplate.Field.SHARES));
    }

    /**
     * Das Brutto einer Dividende: von selbst legt der Lerner dafür keine Regel an — der Betrag steht in
     * der Maske meist als gerechnete Zahl, und eine daran geratene Beschriftung träfe irgendeine Zeile
     * mit demselben Wert. Tippt der Nutzer die Zeile dagegen selbst an, ist nichts mehr zu raten.
     */
    @Test
    public void dasBruttoWirdNurAufAusdrueckicheWahlGelernt() {
        PdfText text = StatementFixtures.ingDividende();
        TemplateLearner.Known ohneWahl = new TemplateLearner.Known();
        ohneWahl.action = StatementScan.DIVIDEND;
        ohneWahl.grossCents = 90699L;
        assertTrue("ohne Wahl darf keine Brutto-Regel entstehen",
                TemplateLearner.learn(text, ohneWahl).rule(StatementTemplate.Field.GROSS) == null);

        AnchorRule gewaehlt = null;
        for (AnchorRule rule : TemplateLearner.kandidaten(
                text, StatementTemplate.Field.GROSS, 906.99)) {
            gewaehlt = rule;
            break;
        }
        assertTrue("für das Brutto wird gar nichts angeboten", gewaehlt != null);

        TemplateLearner.Known mitWahl = new TemplateLearner.Known();
        mitWahl.action = StatementScan.DIVIDEND;
        mitWahl.grossCents = 90699L;
        mitWahl.chosenRules.put(StatementTemplate.Field.GROSS, gewaehlt);
        assertEquals(gewaehlt,
                TemplateLearner.learn(text, mitWahl).rule(StatementTemplate.Field.GROSS));
    }
}
