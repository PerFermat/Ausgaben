package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Ein Merker, der von einer früheren Fundstelle stehenblieb.
 *
 * <p>Beim Lernen der Datumsregel geht die App das Dokument von oben nach unten durch und merkt sich die
 * jeweils beste Lesart. Findet sie das Datum in einer Tabellenzeile ohne eigene Beschriftung, vermerkt
 * sie „das ist eine Tabelle" — dort trifft die Spalte auch dann noch, wenn eine Angabe einmal fehlt.
 * Zurückgesetzt wurde der Vermerk nie. Stand dasselbe Datum weiter unten <b>mit</b> eigener
 * Beschriftung, gewann am Ende trotzdem die Spaltenregel der Tabelle von oben, und die klar benannte
 * Zeile fiel unter den Tisch.</p>
 *
 * <p>Das ist keine erdachte Lage: eine Abrechnung führt oben die Kontobewegung als Tabelle auf und
 * nennt weiter unten „Schlusstag" oder „Valuta" ausdrücklich — beides mit demselben Datum.</p>
 */
public class LearnerDateFlagTest {

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    /**
     * Oben eine Tabellenzeile mit zwei Daten unter einer Überschrift (keine eigene Beschriftung), unten
     * dasselbe Datum hinter „Valuta".
     */
    private static PdfText beleg() {
        return StatementFixtures.of(
                "Wertpapierabrechnung        Kauf",
                "Nominale                    Stück            10,00000",
                "Buchung        Wertstellung      Typ",
                "17.08.2026     17.08.2026        Gutschrift",
                "Kurswert                    EUR          1.000,00",
                "Valuta                      17.08.2026");
    }

    @Test
    public void eineBenannteZeileGewinntGegenDieTabelleWeiterOben() {
        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.BUY;
        bekannt.shares = 10.0;
        bekannt.netCents = 100_000L;
        bekannt.dateMillis = tag(2026, 8, 17);

        StatementTemplate gelernt = TemplateLearner.learn(beleg(), bekannt);
        AnchorRule datum = gelernt.rule(StatementTemplate.Field.DATE);

        assertNotNull("eine Datumsregel wird gelernt", datum);
        assertEquals("gelernt wird die benannte Zeile", "Valuta", datum.anchors.get(0));
        assertEquals("und zwar als Angabe derselben Zeile",
                AnchorRule.Direction.SAME_LINE, datum.direction);
    }
}
