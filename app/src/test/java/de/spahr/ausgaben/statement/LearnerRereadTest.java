package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Eine gelernte Regel muss auf ihrem eigenen Lernbeleg wieder herausgeben, was gelernt wurde.
 *
 * <p>Klingt selbstverständlich, war es aber nicht: {@code singleLine} war der einzige Lernweg, der
 * seine Regel nicht zurückgelesen hat — {@code columnLine} und {@code aboveLine} lassen sich ihre
 * jeweils bestätigen. Aufgefallen ist es an einem Scalable-Verkauf, in dem „STK 170" dreimal steht.
 * Der Lerner nahm „STK" als Beschriftung des Stückpreises; beim Lesen gewinnt aber die
 * <b>unterste</b> Fundstelle (so steht die Endsumme in einer Abrechnung), und das ist die Zeile der
 * Anschaffungsgeschäfte auf Seite 2. Dort ist die letzte Zahl 4.859,11, ein anteiliges Ergebnis —
 * nicht der Kurs.</p>
 *
 * <p>Zugespitzt hat das der Wechsel auf die echte Währungsliste in 1.12: vorher galt „STK" als
 * Währungskürzel und fiel aus jeder Beschriftung heraus, taugte also nie als Anker. Die Lücke lag
 * aber schon vorher offen — jedes mehrfach vorkommende Wort hätte sie aufreißen können.</p>
 */
public class LearnerRereadTest {

    /** Wie der Nutzer den Verkauf erfasst: 170 Stück zu 126,933, Gutschrift 20.805,03. */
    private static TemplateLearner.Known erfasst() {
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = "sell";
        known.shares = 170.0;
        known.price = 126.933;
        known.netCents = 2080503L;
        known.feeCents = 77358L;
        return known;
    }

    /**
     * Der Stückpreis wird wieder gelesen, nicht die letzte Zahl einer gleichnamigen Zeile.
     *
     * <p>Geprüft wird der gelesene Wert, nicht der Anker: welche Beschriftung am Ende gewinnt, darf
     * sich ändern — die Zahl nicht.</p>
     */
    @Test
    public void derGelernteStueckpreisWirdWiederGelesen() {
        PdfText text = StatementFixtures.scalableVerkaufZweiSeitig();

        StatementTemplate.Extraction e = TemplateLearner.learn(text, erfasst()).apply(text);

        assertNotNull("für den Stückpreis entstand gar keine Regel", e.price);
        assertEquals(126.933, e.price, 0.0005);
    }

    /** Dasselbe für die Stückzahl: sie steht in denselben drei Zeilen. */
    @Test
    public void dieGelernteStueckzahlWirdWiederGelesen() {
        PdfText text = StatementFixtures.scalableVerkaufZweiSeitig();

        StatementTemplate.Extraction e = TemplateLearner.learn(text, erfasst()).apply(text);

        assertNotNull("für die Stückzahl entstand gar keine Regel", e.shares);
        assertEquals(170.0, e.shares, 0.0005);
    }

    /**
     * Und die Gutschrift bleibt, was sie war — die zweiseitige Fassung bringt mit der Steuertabelle
     * lauter neue Zahlen mit, unter ihnen den Kurswert 21.578,61, auf den die Regel nicht rutschen darf.
     */
    @Test
    public void derGesamtbetragBleibtDieGutschrift() {
        PdfText text = StatementFixtures.scalableVerkaufZweiSeitig();

        StatementTemplate.Extraction e = TemplateLearner.learn(text, erfasst()).apply(text);

        assertEquals(Long.valueOf(2080503L), e.netCents);
    }
}
