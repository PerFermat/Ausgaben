package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.util.SecurityAmounts;

/**
 * Der ganze Weg: aus einer selbst erfassten Abrechnung lernen, das Gelernte ablegen, wieder laden und
 * damit die nächste Abrechnung auslesen — samt der Gegenprobe, die verhindert, dass eine unstimmige
 * Auslese in die Maske gerät.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementRoundtripTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private static TemplateLearner.Known kauf() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.BUY;
        k.shares = 6.09607;
        k.price = 164.04;
        k.netCents = 100000L;
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        return k;
    }

    private StatementTemplates store() {
        StatementTemplates s = new StatementTemplates(ctx);
        s.clearAll();
        return s;
    }

    @Test
    public void gelerntesÜberstehtDasSpeichernUndLaden() {
        StatementTemplates store = store();
        store.save(TemplateLearner.learn(StatementFixtures.ingKauf(), kauf()));

        StatementTemplate back = new StatementTemplates(ctx).match(StatementFixtures.ingKauf());
        assertNotNull("Vorlage sollte wiedergefunden werden", back);
        assertEquals(StatementScan.BUY, back.action);

        StatementTemplate.Extraction e = back.apply(StatementFixtures.ingKauf());
        assertEquals(6.09607, e.shares, 1e-9);
        assertEquals(164.04, e.price, 1e-9);
        assertEquals(Long.valueOf(100000L), e.netCents);
    }

    @Test
    public void dieSummenregelÜberstehtDasSpeichernAuch() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.shares = 1839.80185;
        k.feeCents = 16746L;
        k.netCents = 73953L;
        StatementTemplates store = store();
        store.save(TemplateLearner.learn(StatementFixtures.ingDividende(), k));

        StatementTemplate back = new StatementTemplates(ctx).match(StatementFixtures.ingDividende());
        assertNotNull(back);
        AnchorRule fee = back.rule(StatementTemplate.Field.FEE);
        assertTrue("Summenregel sollte erhalten bleiben", fee.sum);
        assertEquals(Long.valueOf(16746L), fee.readCents(StatementFixtures.ingDividende()));
    }

    /** Zwei Vorlagen derselben Bank (Kauf und Dividende) dürfen sich nicht verdrängen. */
    @Test
    public void kaufUndDividendeStehenNebeneinander() {
        StatementTemplates store = store();
        store.save(TemplateLearner.learn(StatementFixtures.ingKauf(), kauf()));
        TemplateLearner.Known div = new TemplateLearner.Known();
        div.action = StatementScan.DIVIDEND;
        div.shares = 1839.80185;
        div.feeCents = 16746L;
        div.netCents = 73953L;
        store.save(TemplateLearner.learn(StatementFixtures.ingDividende(), div));

        assertEquals(2, new StatementTemplates(ctx).all().size());
        assertEquals(StatementScan.BUY,
                new StatementTemplates(ctx).match(StatementFixtures.ingKauf()).action);
        assertEquals(StatementScan.DIVIDEND,
                new StatementTemplates(ctx).match(StatementFixtures.ingDividende()).action);
    }

    /** Dieselbe Bank ein zweites Mal gelernt ersetzt die Vorlage, statt eine Dublette anzulegen. */
    @Test
    public void erneutesLernenErsetztStattZuHäufen() {
        StatementTemplates store = store();
        store.save(TemplateLearner.learn(StatementFixtures.ingKauf(), kauf()));
        store.save(TemplateLearner.learn(StatementFixtures.ingKauf(), kauf()));
        assertEquals(1, new StatementTemplates(ctx).all().size());
    }

    /**
     * Wer eine eingelesene Abrechnung nur bestätigt, ohne etwas zu korrigieren, hat der App nichts
     * beizubringen — dann darf auch nicht gefragt werden. Geprüft wird das über den Vergleich: derselbe
     * Text mit denselben Werten ergibt dieselbe Vorlage.
     */
    @Test
    public void nochmalsLernenOhneKorrekturErgibtDieselbeVorlage() {
        StatementTemplate erst = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        StatementTemplate nochmal = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        assertTrue("nichts Neues gelernt", nochmal.sameAs(erst));
    }

    /** Eine Korrektur, deren Wert im Dokument steht, ergibt dagegen eine andere Vorlage. */
    @Test
    public void eineEchteKorrekturErgibtEineAndereVorlage() {
        StatementTemplate erst = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        TemplateLearner.Known k = kauf();
        k.netCents = 100000L;
        k.price = 164.04;
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("19.08.2026");   // Valuta statt Ausführung
        assertFalse(TemplateLearner.learn(StatementFixtures.ingKauf(), k).sameAs(erst));
    }

    /** Eine Korrektur auf einen Wert, der im Dokument nicht vorkommt, lernt nichts Neues. */
    @Test
    public void eineKorrekturAufEinenFremdenWertLerntNichts() {
        StatementTemplate erst = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        TemplateLearner.Known k = kauf();
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("01.01.2020");
        StatementTemplate nachher = TemplateLearner.learn(StatementFixtures.ingKauf(), k);
        // Die Datumsregel entfällt, alles andere bleibt – die Vorlage ist damit nicht identisch,
        // aber sie hat auch nichts Neues über das Datum gelernt.
        assertNull(nachher.rule(StatementTemplate.Field.DATE));
        assertEquals(erst.rule(StatementTemplate.Field.NET), nachher.rule(StatementTemplate.Field.NET));
    }

    /**
     * Zwei Zeilen mit demselben Datum („Zahltag" und „Valuta"): wurde einmal eine davon gelernt, muss sie
     * beim nächsten Mal erhalten bleiben — sonst käme die Rückfrage bei jedem Einlesen erneut.
     */
    @Test
    public void diegelernteDatumsbeschriftungBleibtStehen() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.feeCents = 16746L;
        k.netCents = 73953L;
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        k.dateAnchor = "Zahltag";
        StatementTemplate erst = TemplateLearner.learn(StatementFixtures.ingDividende(), k);

        // Nächster Durchgang: der Nutzer wählt nichts, die Maske reicht den bekannten Anker durch.
        TemplateLearner.Known wieder = new TemplateLearner.Known();
        wieder.action = k.action;
        wieder.feeCents = k.feeCents;
        wieder.netCents = k.netCents;
        wieder.dateMillis = k.dateMillis;
        wieder.dateAnchor = erst.rule(StatementTemplate.Field.DATE).anchors.get(0);
        assertTrue(TemplateLearner.learn(StatementFixtures.ingDividende(), wieder).sameAs(erst));
    }

    private static TemplateLearner.Known dividende() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.feeCents = 16746L;
        k.netCents = 73953L;
        return k;
    }

    /**
     * Der gemeldete Fall. Die Vorlage hat das Datum an „Valuta" festgemacht; in der nächsten Abrechnung
     * lässt die Bank die Zeile weg, weil Valuta und Zahltag derselbe Tag sind.
     *
     * <p>Dann darf nur die <b>Datumsregel</b> ins Leere greifen. Die Vorlage selbst gehört weiterhin zu
     * diesem Dokument — erkannt an der Gesamtsumme —, und Netto und Steuer kommen unverändert an. Sonst
     * bliebe die ganze Maske leer, obwohl alle Beträge sauber im Dokument stehen.</p>
     */
    @Test
    public void eineFehlendeZeileKostetNurIhrEigenesFeld() {
        TemplateLearner.Known k = dividende();
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        k.dateAnchor = "Valuta";
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), k);
        assertEquals("Valuta", t.rule(StatementTemplate.Field.DATE).anchors.get(0));

        assertTrue(t.matches(StatementFixtures.ingDividende()));
        assertTrue("ohne die Valuta-Zeile ist es dieselbe Abrechnung",
                t.matches(StatementFixtures.ingDividendeOhneValuta()));
        // Weniger Treffer als bei der vollständigen – aber immer noch dieselbe Vorlage.
        assertTrue(t.score(StatementFixtures.ingDividendeOhneValuta())
                < t.score(StatementFixtures.ingDividende()));

        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingDividendeOhneValuta());
        assertEquals(-1, e.dateMillis);
        assertEquals(Long.valueOf(73953L), e.netCents);
        assertEquals(Long.valueOf(16746L), e.feeCents);
    }

    /**
     * Fällt kein Solidaritätszuschlag an, druckt die Bank die Zeile nicht. Die Summenregel liefert dann
     * die Steuer der einen vorhandenen Zeile — und nicht etwa gar keine.
     */
    @Test
    public void dieSummenregelKommtAuchMitEinemSummandenAus() {
        TemplateLearner.Known k = dividende();
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), k);
        assertTrue(t.rule(StatementTemplate.Field.FEE).sum);

        PdfText ohneSoli = StatementFixtures.of(
                "Ertragsgutschrift",
                "ISIN (WKN)                        IE00B9CQXS71 (A1T8GD)",
                "Nominale                          1.839,80185 Stück",
                "Zahltag                           17.08.2026",
                "Kapitalertragsteuer 25,00%        EUR             158,73",
                "Gesamtbetrag zu Ihren Gunsten     EUR             748,26");
        assertTrue(t.matches(ohneSoli));
        StatementTemplate.Extraction e = t.apply(ohneSoli);
        assertEquals(Long.valueOf(15873L), e.feeCents);
        assertEquals(Long.valueOf(74826L), e.netCents);
    }

    /**
     * Das Gegenstück dazu: aus einer solchen Abrechnung darf die App die Summenregel nicht verlernen.
     * Sonst läse die nächste vollständige Abrechnung nur noch die Kapitalertragsteuer.
     */
    @Test
    public void ausEinerUnvollstaendigenAbrechnungWirdNichtsVerlernt() {
        StatementTemplate alt = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende());

        // Neu gelernt an einer Abrechnung, in der die Steuer auf einer Zeile steht.
        TemplateLearner.Known k = dividende();
        k.feeCents = 15873L;
        k.netCents = 74826L;
        PdfText ohneSoli = StatementFixtures.of(
                "Ertragsgutschrift",
                "Kapitalertragsteuer 25,00%        EUR             158,73",
                "Gesamtbetrag zu Ihren Gunsten     EUR             748,26");
        StatementTemplate neu = TemplateLearner.learn(ohneSoli, k).mergedOver(alt);

        // Die Stückzahl-Regel stand nicht in dieser Abrechnung und bleibt trotzdem erhalten.
        assertEquals(alt.rule(StatementTemplate.Field.SHARES),
                neu.rule(StatementTemplate.Field.SHARES));
        // Und die Steuer bleibt die Summe beider Zeilen: die vollständige Abrechnung wird weiterhin
        // richtig gelesen, obwohl zuletzt an einer ohne Soli gelernt wurde.
        assertTrue(neu.rule(StatementTemplate.Field.FEE).sum);
        assertEquals(Long.valueOf(16746L),
                neu.apply(StatementFixtures.ingDividende()).feeCents);
    }

    /** Passen zwei Vorlagen auf dasselbe Dokument, gewinnt die mit den meisten Treffern. */
    @Test
    public void dieGenauereVorlageGewinnt() {
        StatementTemplates store = store();
        // Eine karge Vorlage, die nur die Gesamtsumme kennt ...
        java.util.Map<StatementTemplate.Field, AnchorRule> karg =
                new java.util.EnumMap<>(StatementTemplate.Field.class);
        karg.put(StatementTemplate.Field.NET,
                AnchorRule.single("Gesamtbetrag zu Ihren Gunsten", AnchorRule.Direction.SAME_LINE, "EUR"));
        store.save(new StatementTemplate(StatementScan.SELL, karg));
        // ... und die vollständige, an derselben Abrechnung gelernte.
        store.save(TemplateLearner.learn(StatementFixtures.ingDividende(), dividende()));

        assertEquals(StatementScan.DIVIDEND,
                new StatementTemplates(ctx).match(StatementFixtures.ingDividende()).action);
    }

    /**
     * Der Teufelskreis, der überhaupt erst dorthin führt: wird beim Erfassen ein Datum gespeichert, das
     * im Dokument nicht vorkommt — etwa das heutige, weil die Maske es stillschweigend vorgab —, lernt
     * die App <b>keine</b> Datumsregel. Beim nächsten Einlesen bleibt das Datum wieder leer, und der
     * Kreis beginnt von vorn.
     *
     * <p>Deshalb darf die Maske kein Datum erfinden: erst die Wahl aus dem Dokument bricht den Kreis.</p>
     */
    @Test
    public void einDatumDasNichtImDokumentStehtLerntKeineRegel() {
        TemplateLearner.Known k = dividende();
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("25.08.2026");   // heute, nicht im Text
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), k);
        assertNull(t.rule(StatementTemplate.Field.DATE));

        // Ohne Datumsregel passt die Vorlage weiterhin, liefert aber nie ein Datum – die übrigen Werte
        // kommen an, und genau das macht den Mangel so leicht zu übersehen.
        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingDividende());
        assertEquals(-1, e.dateMillis);
        assertEquals(Long.valueOf(73953L), e.netCents);
        assertEquals(Long.valueOf(16746L), e.feeCents);
    }

    /** Die Wahl aus dem Dokument bricht den Kreis: daraus entsteht die Regel, die künftig greift. */
    @Test
    public void dieWahlAusDemDokumentErgibtDieRegel() {
        TemplateLearner.Known k = dividende();
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026");
        k.dateAnchor = "Zahltag";
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), k);

        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026"),
                t.apply(StatementFixtures.ingDividende()).dateMillis);
        // Und diese Beschriftung überlebt das Weglassen der Valuta-Zeile.
        assertTrue(t.matches(StatementFixtures.ingDividendeOhneValuta()));
        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("17.08.2026"),
                t.apply(StatementFixtures.ingDividendeOhneValuta()).dateMillis);
    }

    /**
     * Eine von Hand geordnete Kette muss das Speichern und Laden überstehen — sie ist die eigentliche
     * Handarbeit auf der Regelseite.
     */
    @Test
    public void eineRückfallketteÜberstehtDasSpeichern() {
        StatementTemplates store = store();
        java.util.Map<StatementTemplate.Field, AnchorRule> rules =
                new java.util.EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single("Gesamtbetrag zu Ihren Gunsten", AnchorRule.Direction.SAME_LINE, "EUR"));
        rules.put(StatementTemplate.Field.DATE, new AnchorRule(
                java.util.Arrays.asList("Valuta", "Zahltag", "Ex-Tag"),
                AnchorRule.Direction.SAME_LINE, false));
        rules.put(StatementTemplate.Field.GROSS, new AnchorRule(
                java.util.Arrays.asList("Umg. z. Dev.-Kurs", "Brutto"),
                AnchorRule.Direction.SAME_LINE, false, "EUR"));
        store.saveAll(java.util.Collections.singletonList(
                new StatementTemplate(StatementScan.DIVIDEND, rules)));

        StatementTemplate back = new StatementTemplates(ctx).match(StatementFixtures.ingDividende());
        assertNotNull(back);
        assertEquals(java.util.Arrays.asList("Valuta", "Zahltag", "Ex-Tag"),
                back.rule(StatementTemplate.Field.DATE).anchors);
        // Und die Brutto-Regel, die es nur von Hand gibt, liest den umgerechneten Eurobetrag.
        StatementTemplate.Extraction e = back.apply(StatementFixtures.ingDividende());
        assertEquals(Long.valueOf(90699L), e.grossCents);
    }

    /** saveAll schreibt die Liste, wie sie ist – auch wenn dabei eine Vorlage verschwindet. */
    @Test
    public void saveAllSchreibtDieListeWieSieIst() {
        StatementTemplates store = store();
        store.save(TemplateLearner.learn(StatementFixtures.ingKauf(), kauf()));
        store.save(TemplateLearner.learn(StatementFixtures.ingDividende(), dividende()));
        assertEquals(2, new StatementTemplates(ctx).all().size());

        java.util.List<StatementTemplate> kept = new java.util.ArrayList<>(
                new StatementTemplates(ctx).all());
        kept.remove(0);
        new StatementTemplates(ctx).saveAll(kept);
        assertEquals(1, new StatementTemplates(ctx).all().size());
    }

    /**
     * Der gemeldete Fall: eine Dividende innerhalb des Freibetrags. Abgezogen wird nichts, und die
     * Steuerregel findet folgerichtig keine Zeile.
     *
     * <p>Das Ergebnis muss <b>0</b> sein und nicht {@code null}. Der Unterschied ist der zwischen
     * „stand nicht drin" und „weiß ich nicht" — und nur beim zweiten dürfte etwas anderes einspringen.
     * Zugleich darf keine der Zeilen von Seite 2 (Teilfreistellung, Verrechnungstopf) angefasst werden.</p>
     */
    @Test
    public void eineSteuerregelOhneTrefferBedeutetNull() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), dividende());
        assertNotNull("die Vorlage bringt eine Steuerregel mit",
                t.rule(StatementTemplate.Field.FEE));

        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingDividendeOhneSteuer());
        assertEquals(Long.valueOf(6316L), e.netCents);
        assertEquals(Long.valueOf(0L), e.feeCents);
    }

    /**
     * Bei Kauf und Verkauf bleibt es bei {@code null}: dort bedeutet ein leeres Gebührenfeld ohnehin 0,
     * und ein geschriebenes „0,00" verdeckte nur die Beschriftung.
     */
    @Test
    public void beimKaufBleibtDieFehlendeGebuehrOffen() {
        TemplateLearner.Known k = kauf();
        k.shares = 3.12345;
        k.price = 170.50;
        k.feeCents = 490L;
        k.netCents = 53753L;
        k.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("19.09.2026");
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKaufMitProvision(), k);
        assertNotNull(t.rule(StatementTemplate.Field.FEE));

        // Dieselbe Vorlage auf einen Kauf ohne Provisionszeile.
        assertNull(t.apply(StatementFixtures.ingKauf()).feeCents);
    }

    @Test
    public void ohneGelernteVorlagePasstNichts() {
        store();
        assertNull(new StatementTemplates(ctx).match(StatementFixtures.ingKauf()));
    }

    @Test
    public void dieZuordnungZumWertpapierWirdGemerkt() {
        StatementTemplates store = store();
        store.rememberSecurity("IE00B3RBWM25", "Depot", "E000001", "Vanguard FTSE All-World");
        String[] back = new StatementTemplates(ctx).security("ie00b3rbwm25");
        assertNotNull(back);
        assertEquals("Depot", back[0]);
        assertEquals("E000001", back[1]);
        assertEquals("Vanguard FTSE All-World", back[2]);
        assertNull(new StatementTemplates(ctx).security("IE00B9CQXS71"));
    }

    /**
     * Die Gegenprobe: die ausgelesenen Werte müssen zusammenpassen. Beim Kauf ist der Endbetrag der
     * Betrag plus Gebühr — 6,09607 × 164,04 ergibt 1.000,00 auf den Cent.
     */
    @Test
    public void dieAusleseGehtDurchDieGegenprobe() {
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingKauf(), kauf());
        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingKauf());

        SecurityAmounts.Input in = new SecurityAmounts.Input();
        in.action = e.action;
        in.shares = e.shares;
        in.price = e.price;
        in.netCents = e.netCents;
        SecurityAmounts.Result r = SecurityAmounts.solve(in);
        assertFalse("sollte stimmig sein", r.conflict);
        assertEquals(100000L, (long) r.grossCents);
    }

    /** Der Bruttobetrag der Dividende wird gerechnet, nicht gelesen — im PDF steht er in Dollar. */
    @Test
    public void dasDividendenBruttoWirdGerechnetNichtGelesen() {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.feeCents = 16746L;
        k.netCents = 73953L;
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.ingDividende(), k);
        StatementTemplate.Extraction e = t.apply(StatementFixtures.ingDividende());

        SecurityAmounts.Input in = new SecurityAmounts.Input();
        in.action = StatementScan.DIVIDEND;
        in.feeCents = e.feeCents;
        in.netCents = e.netCents;
        SecurityAmounts.Result r = SecurityAmounts.solve(in);
        assertEquals("906,99 € brutto, nicht 1.053,47 $", 90699L, (long) r.grossCents);
    }

    @Test
    public void einTextOhneInhaltMeldetKeinenText() {
        assertFalse(PdfText.fromLines("").hasText());
        assertTrue(PdfText.fromLines("Nominale Stück 6,09607\nKurs EUR 164,04").hasText());
    }

    /** Der Zwischenspeicher gibt die Zeilen so zurück, dass die Anker weiter greifen. */
    @Test
    public void derZwischengespeicherteTextTrägtDieAnker() {
        PdfText original = StatementFixtures.ingKauf();
        PdfText wieder = PdfText.fromLines(original.text());
        StatementTemplate t = TemplateLearner.learn(original, kauf());
        StatementTemplate.Extraction e = t.apply(wieder);
        assertEquals(6.09607, e.shares, 1e-9);
        assertEquals(Long.valueOf(100000L), e.netCents);
    }
}
