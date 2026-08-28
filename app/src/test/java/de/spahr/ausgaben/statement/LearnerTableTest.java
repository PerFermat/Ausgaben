package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Tabellen: wann der Lerner die <b>Spalte</b> wählt statt die abgezählte Stelle.
 *
 * <p>Beide Formen treffen im Lernmoment denselben Wert — auseinander gehen sie erst bei der
 * <b>nächsten</b> Abrechnung derselben Bank. Fehlt dort eine Spalte, rückt alles dahinter eine Stelle
 * vor, und die gezählte Regel liest still die Nachbarzahl; die Spalte trifft weiter.</p>
 *
 * <p>Bei den Zahlen gewinnt die Spalte deshalb überall, wo sie überhaupt trifft (siehe
 * {@link LearnerColumnTest}). Beim <b>Datum</b> war das anders: dort schlug eine abgezählte Stelle die
 * Spalte, auch wo zwei Datumsangaben nebeneinander unter ihren Überschriften standen. Genau diese Lage
 * wird hier geprüft.</p>
 */
public class LearnerTableTest {

    /** „Buchung / Wertstellung" nebeneinander – die Tabellenlage, um die es geht. */
    private static PdfText dividendeMitTabelle() {
        return StatementFixtures.of(
                "Ertragsgutschrift",
                "Buchung      Wertstellung   Typ",
                "30.06.2026   01.07.2026     Dividende",
                "Gesamt       648,36");
    }

    private static TemplateLearner.Known dividende(long dateMillis) {
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.DIVIDEND;
        bekannt.netCents = 64836L;
        bekannt.dateMillis = dateMillis;
        return bekannt;
    }

    /** Der 01.07.2026 – die Wertstellung, nicht der Buchungstag daneben. */
    private static long wertstellung() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(2026, java.util.Calendar.JULY, 1);
        return c.getTimeInMillis();
    }

    /**
     * Zwei Daten nebeneinander unter ihren Überschriften: das ist eine Tabellenzeile, und dort gilt die
     * Spalte. Abgezählt träfe es die Wertstellung heute auch — aber sobald die Bank die Buchungsspalte
     * einmal wegläßt, läse dieselbe Regel den Buchungstag.
     */
    @Test
    public void inEinerTabellenzeileFolgtDasDatumDerSpalte() {
        AnchorRule regel = TemplateLearner.learn(dividendeMitTabelle(), dividende(wertstellung()))
                .rule(StatementTemplate.Field.DATE);
        assertNotNull(regel);
        assertEquals(AnchorRule.Position.COLUMN, regel.position);
        assertEquals("Wertstellung", regel.anchors.get(0));
        assertEquals(wertstellung(), regel.readDate(dividendeMitTabelle()));
    }

    /** Und die Probe aufs Exempel: fehlt später die Buchungsspalte, stimmt es weiter. */
    @Test
    public void dieSpaltenregelUeberstehtEineFehlendeDatumsspalte() {
        AnchorRule regel = TemplateLearner.learn(dividendeMitTabelle(), dividende(wertstellung()))
                .rule(StatementTemplate.Field.DATE);

        PdfText ohneBuchung = StatementFixtures.of(
                "Ertragsgutschrift",
                "Buchung      Wertstellung   Typ",
                "             01.07.2026     Dividende",
                "Gesamt       648,36");
        assertEquals(wertstellung(), regel.readDate(ohneBuchung));
    }

    /**
     * Die zweite Gegenprobe – und der Grund, warum die Tabellenlogik an eine Bedingung geknüpft ist:
     * <b>derselbe Beleg ohne Wortpositionen</b>.
     *
     * <p>Durch {@link PdfText#fromLines} gejagt, hängen die Wörter mit genau einem Leerzeichen
     * aneinander; von der Tabelle ist nichts mehr übrig. Dort greift die Tabellenerkennung deshalb gar
     * nicht, und es bleibt bei der Regel, die vor dieser Änderung entstanden wäre. Genau diesen Weg
     * nimmt der Bestandstest mit seinen Textdateien — er kann über die Tabellenlogik nichts aussagen.</p>
     */
    @Test
    public void ohneWortpositionenGreiftDieTabellenlogikNicht() {
        PdfText alsText = PdfText.fromLines(dividendeMitTabelle().text());
        AnchorRule regel = TemplateLearner.learn(alsText, dividende(wertstellung()))
                .rule(StatementTemplate.Field.DATE);
        assertNotNull(regel);
        assertNotEquals("ohne Koordinaten gibt es keine Spalte zu treffen",
                AnchorRule.Position.COLUMN, regel.position);
    }

    /**
     * Die Gegenprobe: trägt die Datumszeile eine eigene Beschriftung, bleibt es dabei. Eine
     * Beschriftung in derselben Zeile ist die klarere Auskunft als eine Spaltenüberschrift darüber —
     * sie steht neben ihrem Wert und verrutscht nicht.
     */
    @Test
    public void mitEigenerBeschriftungBleibtEsBeiDerselbenZeile() {
        PdfText mitBeschriftung = StatementFixtures.of(
                "Ertragsgutschrift",
                "Valuta       01.07.2026",
                "Gesamt       648,36");
        TemplateLearner.Known bekannt = dividende(wertstellung());

        AnchorRule regel = TemplateLearner.learn(mitBeschriftung, bekannt)
                .rule(StatementTemplate.Field.DATE);
        assertNotNull(regel);
        assertEquals(AnchorRule.Direction.SAME_LINE, regel.direction);
        assertEquals("Valuta", regel.anchors.get(0));
    }
}
