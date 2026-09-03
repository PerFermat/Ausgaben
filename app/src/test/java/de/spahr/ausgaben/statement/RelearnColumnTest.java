package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.SecurityAmounts;

/**
 * Eine schon gelernte Spaltenregel richtigstellen: die Vorlage liest die Stückzahl unter „Nominale",
 * der Nutzer trägt aber eine andere Zahl aus dem Beleg ein und wählt übers Stift-Symbol deren
 * Beschriftung. Was dabei herauskommt, muss sich von der gespeicherten Vorlage unterscheiden — sonst
 * bricht {@code learnFrom} wortlos ab und nichts wird gemerkt.
 */
public class RelearnColumnTest {

    /** Der Beleg mit der Zeile, die es hier braucht: „Lagerstelle: 1419". */
    private static PdfText beleg() {
        return StatementFixtures.of(
                "Wertpapierabrechnung: Kauf",
                "Auftragsdatum: 07.08.2024        Ausführungsplatz: GETTEX - MM Munich",
                "Nominale                         ISIN: IE00B3RBWM25    WKN: A1JX52      Kurs",
                "STK 86                           Vanguard FTSE All-World U.ETF          EUR    116,20",
                "                                 Registered Shares USD Dis.oN",
                "Kurswert                                                                 EUR   9.993,20",
                "Zu Lasten Konto 1234567890       Valuta: 09.08.2024                      EUR   9.993,20",
                "Details zur Ausführung:",
                "Nominale        Kurs             Ausführungsplatz            Handelsdatum   Handelsuhrzeit",
                "STK  86         EUR  116,20      GETTEX - MM Munich          07.08.2024     19:13:49:506",
                "Verwahrart:     Wertpapierrechnung",
                "Lagerstelle:    1419",
                "Lagerland:      Luxemburg");
    }

    /** Die Vorlage, wie sie nach dem ersten Lernlauf dasteht: die Stückzahl hängt an der Spalte. */
    private static StatementTemplate vorlage(PdfText text) {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, new AnchorRule(Arrays.asList("Kurswert"),
                AnchorRule.Direction.SAME_LINE, false, "EUR"));
        rules.put(StatementTemplate.Field.DATE, new AnchorRule(Arrays.asList("Valuta"),
                AnchorRule.Direction.SAME_LINE, false));
        AnchorRule spalte = new AnchorRule(Arrays.asList("Nominale"),
                AnchorRule.Direction.LINE_BELOW, false, "", AnchorRule.Position.COLUMN);
        assertEquals("die Vorlage liest die 86 nicht", 86.0, spalte.read(text), 1e-9);
        rules.put(StatementTemplate.Field.SHARES, spalte);
        return new StatementTemplate(StatementScan.BUY, rules);
    }

    @Test
    public void eineKorrekturAufEineAndereZahlErgibtEineAndereVorlage() {
        PdfText text = beleg();
        StatementTemplate alt = vorlage(text);

        List<AnchorRule> kandidaten = TemplateLearner.kandidaten(
                text, StatementTemplate.Field.SHARES, 1419.0);
        assertFalse("für 1419 wird gar keine Beschriftung angeboten", kandidaten.isEmpty());

        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 1419.0;
        k.chosenRules.put(StatementTemplate.Field.SHARES, kandidaten.get(0));
        StatementTemplate raw = TemplateLearner.learn(text, k);

        AnchorRule neu = raw.rule(StatementTemplate.Field.SHARES);
        assertNotNull("der Lerner hat für 1419 gar keine Regel gebaut", neu);
        assertEquals("nicht die gewählte Beschriftung gelernt", kandidaten.get(0), neu);

        StatementTemplate ersetzt = raw.mergedOver(alt);
        assertFalse("die Vorlage bleibt unverändert – learnFrom bräche hier wortlos ab",
                ersetzt.sameAs(alt));
        assertEquals(1419.0, ersetzt.rule(StatementTemplate.Field.SHARES).read(text),
                SecurityAmounts.SHARE_EPSILON);
    }

    /**
     * Die Falle, die das Nachlernen zum zweiten Mal scheitern liess: Steht in der Vorlage schon eine
     * <b>Kette</b> — etwa weil beim vorigen Mal „hinzufügen" gewählt wurde —, sieht eine daraus
     * gewählte einzelne Beschriftung für {@code mergedOver} wie ein Auszug aus. Gegen eine
     * unvollständige Abrechnung ist dieses Festhalten richtig; gegen die ausdrückliche Wahl im
     * Stift-Dialog ist es ein stilles Verwerfen, und {@code learnFrom} bricht dann wortlos ab.
     */
    @Test
    public void eineWahlAusDerVorhandenenKetteWirdNichtAlsAuszugVerworfen() {
        PdfText text = beleg();
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.SHARES, new AnchorRule(
                Arrays.asList("Lagerstelle:", "Nominale"), AnchorRule.Direction.SAME_LINE, false));
        StatementTemplate alt = new StatementTemplate(StatementScan.BUY, rules);

        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 1419.0;
        AnchorRule gewaehlt = new AnchorRule(Arrays.asList("Lagerstelle:"),
                AnchorRule.Direction.SAME_LINE, false);
        k.chosenRules.put(StatementTemplate.Field.SHARES, gewaehlt);
        StatementTemplate raw = TemplateLearner.learn(text, k);

        // So, wie learnFrom es jetzt tut: die ausdrücklich ersetzten Felder kommen direkt aus `raw`.
        StatementTemplate ersetzt = raw.mergedOver(alt).withRulesFrom(raw,
                java.util.EnumSet.of(StatementTemplate.Field.SHARES));
        assertEquals(gewaehlt, ersetzt.rule(StatementTemplate.Field.SHARES));
        assertFalse("die Wahl wurde als Auszug verworfen", ersetzt.sameAs(alt));

        // Und ohne diese Weiche wäre genau das passiert – der Beleg für die Falle.
        assertEquals(alt.rule(StatementTemplate.Field.SHARES),
                raw.mergedOver(alt).rule(StatementTemplate.Field.SHARES));
    }
}
