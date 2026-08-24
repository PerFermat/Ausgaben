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
