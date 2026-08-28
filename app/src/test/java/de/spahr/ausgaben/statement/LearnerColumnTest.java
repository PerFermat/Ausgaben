package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Was der Lerner mit den beiden neuen Formen anfängt: der Spalte einer Überschrift und dem Wert
 * <b>über</b> seiner Beschriftung.
 *
 * <p>Beide stehen in der Reihenfolge des Lerners <b>hinten</b> — er greift erst danach, wenn weder eine
 * Beschriftung in derselben Zeile noch eine Überschrift mit abzählbarer Stelle etwas hergibt. Das ist
 * Absicht: an allem, was bisher schon gelernt wurde, ändert sich damit nichts, und die Messwerte am
 * fremden Bestand bleiben vergleichbar.</p>
 */
public class LearnerColumnTest {

    /**
     * Eine breite Tabelle: der gesuchte Betrag ist die vierte Zahl — von links wie von rechts. Damit
     * liegt er ausserhalb dessen, was der Lerner abzählt (er geht drei Stellen weit; tiefer zu zählen
     * träfe zu oft zufällig eine andere Zahl).
     */
    private static PdfText breiteTabelle() {
        return StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "ISIN IE00B3RBWM25",
                "Stück   Kurs      Provision  Gesamt   Steuer   Spesen   Bestand",
                "10,000  100,00    9,90       999,00   2,20     1,10     1234,00");
    }

    @Test
    public void ausDerBreitenTabelleWirdEineSpaltenregel() {
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.netCents = 99900L;

        StatementTemplate gelernt = TemplateLearner.learn(breiteTabelle(), bekannt);
        AnchorRule regel = gelernt.rule(StatementTemplate.Field.NET);
        assertNotNull("ohne die Spalte bliebe das Feld leer", regel);
        assertEquals(AnchorRule.Position.COLUMN, regel.position);
        assertEquals("Gesamt", regel.anchors.get(0));
        assertEquals("und die gelernte Regel liest denselben Betrag wieder",
                999.0, regel.read(breiteTabelle()), 0.0001);
    }

    /**
     * Die Probe aufs Exempel: dieselbe Bank, dasselbe Muster, aber ohne die Spesenspalte. Die gelernte Regel
     * liest weiter richtig — eine gezählte Stelle täte das nicht.
     */
    @Test
    public void dieGelernteSpaltenregelHaeltAuchOhneEineSpalte() {
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.netCents = 99900L;
        AnchorRule regel = TemplateLearner.learn(breiteTabelle(), bekannt)
                .rule(StatementTemplate.Field.NET);

        PdfText ohneSpesen = StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "ISIN IE00B3RBWM25",
                "Stück   Kurs      Provision  Gesamt   Steuer   Spesen   Bestand",
                "10,000  100,00    9,90       999,00   2,20              1234,00");
        assertEquals(999.0, regel.read(ohneSpesen), 0.0001);
    }

    /**
     * Der Wert über seiner Beschriftung: hier trägt die Zeile mit dem Betrag keine eigene, und über ihr
     * steht auch keine — die Benennung folgt erst darunter.
     */
    @Test
    public void ausEinerUnterschriftWirdEineRegelNachOben() {
        PdfText beleg = StatementFixtures.of(
                "999,00",
                "Ausmachender Betrag in EUR",
                "ISIN IE00B3RBWM25");

        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.netCents = 99900L;

        AnchorRule regel = TemplateLearner.learn(beleg, bekannt).rule(StatementTemplate.Field.NET);
        assertNotNull(regel);
        assertEquals(AnchorRule.Direction.LINE_ABOVE, regel.direction);
        assertEquals(999.0, regel.read(beleg), 0.0001);
    }

    /**
     * Die Gegenprobe zur Reihenfolge: wo die alte Suche etwas findet, bleibt sie es auch. Der
     * ING-Kaufbeleg lernt weiterhin eine Beschriftung in derselben Zeile.
     */
    @Test
    public void amGewohntenBelegAendertSichNichts() {
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.shares = 6.09607;
        bekannt.netCents = 100000L;

        AnchorRule regel = TemplateLearner.learn(StatementFixtures.ingKauf(), bekannt)
                .rule(StatementTemplate.Field.NET);
        assertNotNull(regel);
        assertEquals(AnchorRule.Direction.SAME_LINE, regel.direction);
        assertEquals(AnchorRule.Position.LAST, regel.position);
    }
}
