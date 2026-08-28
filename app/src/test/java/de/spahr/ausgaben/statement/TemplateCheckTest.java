package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die Probe aufs Gelernte: was {@link TemplateCheck} an einer frisch gemerkten Vorlage bemängelt — und
 * vor allem, was nicht.
 *
 * <p>Der zweite Teil ist der wichtigere. Eine Prüfung, die zu oft anschlägt, wird weggeklickt und misst
 * dann gar nichts mehr. Deshalb steht hier ausdrücklich, welche Lagen <b>keine</b> Rückmeldung
 * hervorrufen: ein Feld ohne Wert in der Maske, ein nicht angefasstes Feld ohne Regel, und eine feste
 * Ordergebühr, die gar nicht im Dokument steht.</p>
 */
public class TemplateCheckTest {

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    /** Ein schlichter Kaufbeleg mit Gebühr. */
    private static PdfText beleg() {
        return StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "ISIN IE00B3RBWM25",
                "Valuta                      19.08.2026",
                "Provision                   EUR                9,90",
                "Endbetrag zu Ihren Lasten   EUR            1.000,00");
    }

    private static StatementTemplate vorlage(String netAnchor, String feeAnchor) {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.DATE,
                AnchorRule.single("Valuta", AnchorRule.Direction.SAME_LINE));
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single(netAnchor, AnchorRule.Direction.SAME_LINE));
        if (feeAnchor != null) {
            rules.put(StatementTemplate.Field.FEE,
                    AnchorRule.single(feeAnchor, AnchorRule.Direction.SAME_LINE));
        }
        return new StatementTemplate("buy", rules);
    }

    /** Was in der Maske stand: das, was die Vorlage oben auch wirklich findet. */
    private static TemplateCheck.Expected maske() {
        TemplateCheck.Expected soll = new TemplateCheck.Expected();
        soll.action = "buy";
        soll.dateMillis = tag(2026, 8, 19);
        soll.netCents = 100000L;
        soll.feeCents = 990L;
        soll.typed.add(StatementTemplate.Field.NET);
        soll.typed.add(StatementTemplate.Field.FEE);
        return soll;
    }

    @Test
    public void wennAllesStimmtGibtEsNichtsZuSagen() {
        assertTrue(TemplateCheck.check(vorlage("Endbetrag zu Ihren Lasten", "Provision"),
                beleg(), maske()).isEmpty());
    }

    /** Die Regel greift, liest aber die falsche Zeile — Feld, Soll und Ist stehen im Mangel. */
    @Test
    public void eineDanebenGreifendeRegelWirdMitSollUndIstGenannt() {
        List<TemplateCheck.Complaint> maengel =
                TemplateCheck.check(vorlage("Provision", "Provision"), beleg(), maske());
        assertEquals(1, maengel.size());
        TemplateCheck.Complaint c = maengel.get(0);
        assertEquals(StatementTemplate.Field.NET, c.field);
        assertEquals(TemplateCheck.Kind.WRONG, c.kind);
        assertEquals(1000.0, c.expected, 0.0001);
        assertEquals(9.90, c.actual, 0.0001);
    }

    /** Die Beschriftung steht gar nicht im Dokument: „nichts gefunden". */
    @Test
    public void eineRegelDieNichtsFindet() {
        List<TemplateCheck.Complaint> maengel =
                TemplateCheck.check(vorlage("Ausmachender Betrag", "Provision"), beleg(), maske());
        assertEquals(1, maengel.size());
        assertEquals(StatementTemplate.Field.NET, maengel.get(0).field);
        assertEquals(TemplateCheck.Kind.NOT_FOUND, maengel.get(0).kind);
    }

    /**
     * Ein Feld, das der Nutzer eigens berichtigt hat, für das aber gar keine Regel entstanden ist. Genau
     * dafür hat er getippt — das darf nicht stillschweigend untergehen.
     */
    @Test
    public void einSelbstBerichtigtesFeldOhneRegelIstEinMangel() {
        List<TemplateCheck.Complaint> maengel =
                TemplateCheck.check(vorlage("Endbetrag zu Ihren Lasten", null), beleg(), maske());
        assertEquals(1, maengel.size());
        assertEquals(StatementTemplate.Field.FEE, maengel.get(0).field);
        assertEquals(TemplateCheck.Kind.NO_RULE, maengel.get(0).kind);
    }

    /**
     * Dasselbe Feld, aber vom Nutzer nicht angefasst: dann ist die fehlende Regel kein Mangel. Brutto
     * und Kurs rechnet die Maske sich selbst aus; für sie gibt es mit gutem Grund oft keine Regel.
     */
    @Test
    public void einNichtAngefasstesFeldOhneRegelBleibtStumm() {
        TemplateCheck.Expected soll = maske();
        soll.typed.remove(StatementTemplate.Field.FEE);
        assertTrue(TemplateCheck.check(vorlage("Endbetrag zu Ihren Lasten", null), beleg(), soll)
                .isEmpty());
    }

    /** Wo die Maske nichts trägt, gibt es nichts zu vergleichen — auch nicht bei einer Regel. */
    @Test
    public void ohneWertInDerMaskeWirdNichtsGemessen() {
        TemplateCheck.Expected soll = maske();
        soll.feeCents = null;
        assertTrue(TemplateCheck.check(vorlage("Endbetrag zu Ihren Lasten", "Ausmachender Betrag"),
                beleg(), soll).isEmpty());
    }

    /**
     * Die feste Ordergebühr steht nirgends im Dokument — sie kommt aus der Vorlage. Geprüft wird
     * deshalb über {@link StatementTemplate#apply}: dort wird sie angesetzt, und die Gebühr stimmt.
     */
    @Test
    public void dieFesteOrdergebuehrZaehltAlsGefunden() {
        PdfText ohneGebuehr = StatementFixtures.of(
                "Wertpapierabrechnung Kauf",
                "Valuta                      19.08.2026",
                "Endbetrag zu Ihren Lasten   EUR            1.000,00");
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.DATE,
                AnchorRule.single("Valuta", AnchorRule.Direction.SAME_LINE));
        rules.put(StatementTemplate.Field.NET, new AnchorRule(
                Collections.singletonList("Endbetrag zu Ihren Lasten"),
                AnchorRule.Direction.SAME_LINE, false, "", AnchorRule.Position.LAST, 1, 0));
        StatementTemplate mitFester = new StatementTemplate("buy", rules)
                .withFixedFee(990L, "Gebühren", true);

        TemplateCheck.Expected soll = maske();
        assertTrue(TemplateCheck.check(mitFester, ohneGebuehr, soll).isEmpty());
    }

    /** Beim Datum zählt der Kalendertag: die Maske trägt die Uhrzeit der Eingabe mit sich. */
    @Test
    public void beimDatumZaehltDerTagNichtDieUhrzeit() {
        TemplateCheck.Expected soll = maske();
        soll.dateMillis = tag(2026, 8, 19) + 13 * 3600_000L;
        assertTrue(TemplateCheck.check(vorlage("Endbetrag zu Ihren Lasten", "Provision"),
                beleg(), soll).isEmpty());

        soll.dateMillis = tag(2026, 8, 20);
        List<TemplateCheck.Complaint> maengel = TemplateCheck.check(
                vorlage("Endbetrag zu Ihren Lasten", "Provision"), beleg(), soll);
        assertEquals(1, maengel.size());
        assertEquals(StatementTemplate.Field.DATE, maengel.get(0).field);
    }

    /**
     * Das <b>Brutto</b> wird nie als „keine Regel gelernt" bemängelt: der Lerner legt dafür mit Absicht
     * keine an, es wird in der Maske gerechnet. Bei einer Dividende tippt man es trotzdem selbst ein —
     * ohne diese Ausnahme käme nach jeder Dividende eine Rückmeldung über einen Fehler, den es nicht
     * gibt.
     */
    @Test
    public void fuersBruttoWirdKeineFehlendeRegelBemaengelt() {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single("Endbetrag zu Ihren Lasten", AnchorRule.Direction.SAME_LINE));
        TemplateCheck.Expected soll = new TemplateCheck.Expected();
        soll.action = "dividend";
        soll.netCents = 100000L;
        soll.grossCents = 120000L;
        soll.typed.add(StatementTemplate.Field.GROSS);
        assertTrue(TemplateCheck.check(new StatementTemplate("dividend", rules), beleg(), soll)
                .isEmpty());
    }

    /** Bei einer Dividende werden Anzahl und Kurs gar nicht geprüft — sie gehören nicht dazu. */
    @Test
    public void beiEinerDividendeBleibenAnzahlUndKursAussen() {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single("Endbetrag zu Ihren Lasten", AnchorRule.Direction.SAME_LINE));
        TemplateCheck.Expected soll = new TemplateCheck.Expected();
        soll.action = "dividend";
        soll.netCents = 100000L;
        soll.shares = 6.09607;          // stünde in der Maske einer Dividende gar nicht
        soll.typed.add(StatementTemplate.Field.SHARES);
        assertTrue(TemplateCheck.check(new StatementTemplate("dividend", rules), beleg(), soll)
                .isEmpty());
    }
}
