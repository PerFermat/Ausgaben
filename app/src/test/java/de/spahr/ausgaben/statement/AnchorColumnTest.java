package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die Spalte der Beschriftung: der Wert wird nicht abgezählt, sondern über seine Position auf der Seite
 * getroffen ({@link AnchorRule.Position#COLUMN}).
 *
 * <p>Der Unterschied zeigt sich erst an einer Tabelle. Abzählen stimmt nur, solange jede Spalte genau
 * eine Zahl breit ist und keine fehlt: eine zweiwortige Überschrift, ein Währungskürzel als eigene
 * Spalte oder eine ausgelassene Angabe verschieben jede gezählte Stelle — still, mit der Zahl der
 * Nachbarspalte als Ergebnis.</p>
 *
 * <p>Die x-Werte kommen aus {@link StatementFixtures}: dort wird die Spalte im Quelltext zur Position auf
 * der Seite, „steht darunter" bedeutet hier also wirklich etwas.</p>
 */
public class AnchorColumnTest {

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    /**
     * Eine Anleihe-Tabelle: vor dem ersten Wert steht ein Währungskürzel, hinter zwei weiteren je ein
     * Prozentzeichen. Genau die Zeichen, die beim Abzählen mitzählen würden.
     */
    private static PdfText anleihe() {
        return StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "Nominale        Kurs      Zinssatz     Betrag",
                "EUR 2.000,00    98,75 %   8,750 %      1.975,00 EUR");
    }

    private static AnchorRule spalte(String anchor, AnchorRule.Direction direction, int distance) {
        return new AnchorRule(java.util.Collections.singletonList(anchor), direction, false, "",
                AnchorRule.Position.COLUMN, 1, distance);
    }

    // ---- Treffer ----

    /** Der Regelfall: die Zahl steht direkt unter ihrer Überschrift. */
    @Test
    public void dieZahlUnterDerUeberschriftGewinnt() {
        assertEquals(98.75, spalte("Kurs", AnchorRule.Direction.LINE_BELOW, 1).read(anleihe()), 0.0001);
        assertEquals(1975.0,
                spalte("Betrag", AnchorRule.Direction.LINE_BELOW, 1).read(anleihe()), 0.0001);
    }

    /**
     * Das Währungskürzel vor dem Wert zählt nicht als Spalte — sonst läse „Nominale" das „EUR" und
     * fände gar nichts.
     */
    @Test
    public void dasWaehrungskuerzelIstKeinWert() {
        assertEquals(2000.0,
                spalte("Nominale", AnchorRule.Direction.LINE_BELOW, 1).read(anleihe()), 0.0001);
    }

    /**
     * Das Prozentzeichen ebensowenig. Es steht rechts neben dem Zinssatz und wäre ohne diese Regel der
     * nächstliegende Treffer für die Überschrift „Zinssatz" — mit „kein Wert" als Ergebnis.
     */
    @Test
    public void dasProzentzeichenIstKeinWert() {
        assertEquals(8.75,
                spalte("Zinssatz", AnchorRule.Direction.LINE_BELOW, 1).read(anleihe()), 0.0001);
    }

    /**
     * Zahlenspalten sind rechtsbündig gesetzt, Überschriften linksbündig — dann überschneidet sich
     * nichts, obwohl es dieselbe Spalte ist. In {@link StatementFixtures#tabellenDividende()} endet die
     * Überschrift „Gesamt" erst hinter dem Betrag 648,36; er gewinnt trotzdem, weil seine Mitte am
     * nächsten liegt.
     */
    @Test
    public void ohneUeberschneidungGewinntDerGeringsteAbstand() {
        assertEquals(648.36,
                spalte("Gesamt", AnchorRule.Direction.LINE_BELOW, 0)
                        .read(StatementFixtures.tabellenDividende()), 0.0001);
    }

    /** Ein Datum in der Spalte: die Wertstellung, nicht der Buchungstag daneben. */
    @Test
    public void auchDasDatumFolgtDerSpalte() {
        assertEquals(tag(2026, 7, 1),
                spalte("Wertstellung", AnchorRule.Direction.LINE_BELOW, 2)
                        .readDate(StatementFixtures.tabellenDividende()));
        assertEquals("und die Spalte daneben ist der Buchungstag", tag(2026, 6, 30),
                spalte("Buchung", AnchorRule.Direction.LINE_BELOW, 2)
                        .readDate(StatementFixtures.tabellenDividende()));
    }

    /** Auch nach oben: dieselbe Spalte, die Beschriftung unter den Daten. */
    @Test
    public void dieSpalteGiltAuchNachOben() {
        PdfText beleg = StatementFixtures.of(
                "EUR 2.000,00    98,75 %   8,750 %      1.975,00 EUR",
                "Nominale        Kurs      Zinssatz     Betrag");
        assertEquals(1975.0, spalte("Betrag", AnchorRule.Direction.LINE_ABOVE, 1).read(beleg), 0.0001);
    }

    /**
     * Unter mehreren Zahlen, die alle im Bereich einer <b>breiten</b> Beschriftung liegen, gewinnt die
     * mittigste — nicht die erste.
     *
     * <p>„Betrag in Euro je Stueck" ist fünf Wörter breit und überdeckt damit die halbe Zeile. Die erste
     * Zahl darunter steht ganz links am Rand dieses Bereichs; gemeint ist die, die unter der Mitte der
     * Beschriftung steht.</p>
     */
    @Test
    public void unterMehrerenGewinntDieMittigste() {
        PdfText beleg = StatementFixtures.of(
                "Betrag in Euro je Stueck",
                "2,00       5,00        9,00");
        assertEquals(5.0, spalte("Betrag in Euro je Stueck", AnchorRule.Direction.LINE_BELOW, 1)
                .read(beleg), 0.0001);
    }

    /**
     * Eine Überschneidung geht dem geringeren Abstand vor.
     *
     * <p>Der Fall, in dem sich beides unterscheidet, braucht eine ungewöhnlich <b>breite</b> Zahl: sie
     * reicht bis unter die Überschrift, hat aber wegen ihrer Länge eine Mitte weit links davon, während
     * die schmale Zahl der Nachbarspalte zufällig näher an der Mitte liegt. Gemeint ist die breite —
     * sie steht in dieser Spalte, die andere daneben.</p>
     */
    @Test
    public void eineUeberschneidungGehtDemAbstandVor() {
        PdfText beleg = StatementFixtures.of(
                "                                  Summe",
                "                  1.234.567.890,00      9,00");
        assertEquals(1234567890.0,
                spalte("Summe", AnchorRule.Direction.LINE_BELOW, 1).read(beleg), 0.01);
    }

    // ---- Grenzen ----

    /**
     * In derselben Zeile ist die Spalte der Beschriftung die Beschriftung selbst — die Angabe liefert
     * dort nichts. Die Regelseite lässt die Kombination gar nicht erst zu.
     */
    @Test
    public void inDerselbenZeileFindetDieSpalteNichts() {
        assertNull(spalte("Kurs", AnchorRule.Direction.SAME_LINE, 0).read(anleihe()));
    }

    /** Steht in der Zielzeile keine Zahl, gibt es auch keinen Treffer. */
    @Test
    public void ohneZahlInDerZeileKeinTreffer() {
        PdfText beleg = StatementFixtures.of(
                "Nominale        Kurs      Zinssatz     Betrag",
                "keine Angabe    keine     keine        keine");
        assertNull(spalte("Kurs", AnchorRule.Direction.LINE_BELOW, 1).read(beleg));
    }

    /** Ohne die Beschriftung im Beleg schlägt nichts an. */
    @Test
    public void ohneBeschriftungKeinTreffer() {
        assertNull(spalte("Ausmachender Betrag", AnchorRule.Direction.LINE_BELOW, 1).read(anleihe()));
    }

    // ---- Der Unterschied zum Abzählen ----

    /**
     * Die Gegenprobe zum Zweck der ganzen Übung: derselbe Beleg einmal <b>mit</b> und einmal
     * <b>ohne</b> die Kursspalte, wie ihn zwei verschiedene Papiere derselben Bank ergeben.
     *
     * <p>Eine am ersten gelernte, gezählte Regel liest im zweiten den Betrag als Zinssatz — die Spalte
     * fehlt, und alles rechts davon rückt eine Stelle vor. Über die Position stimmt es weiter.</p>
     */
    @Test
    public void beiFehlenderSpalteVerzaehltSichDasAbzaehlen() {
        PdfText ohneKurs = StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "Nominale        Kurs      Zinssatz     Betrag",
                "EUR 2.000,00              8,750 %      1.975,00 EUR");

        AnchorRule gezaehlt = new AnchorRule(java.util.Collections.singletonList("Zinssatz"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.FIRST, 3, 1);
        assertEquals("am vollständigen Beleg gelernt: die dritte Zahl von links ist der Zinssatz",
                8.75, gezaehlt.read(anleihe()), 0.0001);
        assertEquals("fehlt der Kurs, ist die dritte Zahl der Betrag – still und falsch",
                1975.0, gezaehlt.read(ohneKurs), 0.0001);

        AnchorRule ueberDieSpalte = spalte("Zinssatz", AnchorRule.Direction.LINE_BELOW, 1);
        assertEquals(8.75, ueberDieSpalte.read(anleihe()), 0.0001);
        assertEquals("über die Position stimmt es auch ohne die Kursspalte",
                8.75, ueberDieSpalte.read(ohneKurs), 0.0001);
    }
}
