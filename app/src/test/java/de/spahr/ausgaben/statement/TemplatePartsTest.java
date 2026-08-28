package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.settings.StatementTemplates;

/**
 * Die Aufschlüsselung der Steuer in ihre einzelnen Zeilen: gelernt, angewandt und zusammengeführt.
 *
 * <p>Die Summenregel bleibt daneben bestehen — sie liest weiterhin den Gesamtbetrag. Die Teile sagen
 * nur, aus welchen Zeilen er sich zusammensetzt, damit jede unter ihre eigene Kategorie kann.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TemplatePartsTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /** Die Steuerzeilen der Maske: Betrag und die Kategorie, unter der sie gebucht werden. */
    private static StatementTemplate gelernt(PdfText text, long... teile) {
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = "dividend";
        known.netCents = 73953L;
        known.feeCents = 16746L;
        for (long cents : teile) {
            known.feeParts.add(new StatementTemplate.Part("", cents, "Steuern " + cents));
        }
        return TemplateLearner.learn(text, known);
    }

    private static StatementTemplate.PartRule mit(List<StatementTemplate.PartRule> parts, String label) {
        for (StatementTemplate.PartRule part : parts) {
            if (part.label.equalsIgnoreCase(label)) {
                return part;
            }
        }
        return null;
    }

    /**
     * Der Scalable-Verkauf, wie ihn der Nutzer erfasst: die Gutschrift 20.805,03, dazu 773,58 an
     * Steuern und Gebühren, aufgeteilt auf die drei ausgewiesenen Steuerzeilen und die Bankgebühr,
     * die nirgends im Beleg steht.
     */
    private static StatementTemplate verkaufGelernt() {
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = "sell";
        known.shares = 170.0;
        known.price = 126.933;
        known.netCents = 2080503L;
        known.feeCents = 77358L;
        known.feeParts.add(new StatementTemplate.Part("", 68071L, "Steuern:Kapitalertragsteuer"));
        known.feeParts.add(new StatementTemplate.Part("", 5445L, "Steuern:Kirchensteuer"));
        known.feeParts.add(new StatementTemplate.Part("", 3743L, "Steuern:Solidaritätszuschlag"));
        known.feeParts.add(new StatementTemplate.Part("", 99L, "Ausgaben:Bankgebühren"));
        return TemplateLearner.learn(StatementFixtures.scalableVerkauf(), known);
    }

    /**
     * Fest ist nur, was <b>nicht</b> im Beleg steht.
     *
     * <p>Die drei Steuerzeilen findet der Lerner; als feste Ordergebühr bleibt allein die Bankgebühr
     * von 0,99. Vorher wurden daraus 773,58 — die ganze Steuer galt als Gebühr der Bank, und beim
     * nächsten Einlesen stand sie unter deren Kategorie.</p>
     */
    @Test
    public void festIstNurWasNichtImBelegSteht() {
        StatementTemplate t = verkaufGelernt();
        assertEquals(99L, t.fixedFeeCents);
        assertEquals("Ausgaben:Bankgebühren", t.fixedFeeCategory);
        assertEquals(3, t.feeParts.size());
    }

    /**
     * Und der Gesamtbetrag wird dort gesucht, wo er steht: 20.805,03 + 0,99 ergibt die Gutschrift
     * 20.806,02. Mit der ganzen Steuer als fester Gebühr kam 21.578,61 heraus — der <b>Kurswert</b>,
     * und der geht bei jeder anderen Abrechnung dieser Bank an der Gutschrift vorbei.
     */
    @Test
    public void derGesamtbetragTrifftDieGutschriftUndNichtDenKurswert() {
        StatementTemplate t = verkaufGelernt();
        assertEquals(Long.valueOf(2080602L),
                t.rule(StatementTemplate.Field.NET).readCents(StatementFixtures.scalableVerkauf()));
    }

    /** Angewandt kommt genau das wieder heraus, was eingetragen war — vier Zeilen, Summe 773,58. */
    @Test
    public void derVerkaufWirdWiederVollstaendigGelesen() {
        StatementTemplate.Extraction e = verkaufGelernt().apply(StatementFixtures.scalableVerkauf());
        assertEquals(Long.valueOf(77358L), e.feeCents);
        assertEquals(Long.valueOf(2080503L), e.netCents);
        assertEquals(4, e.feeParts.size());
        long summe = 0;
        for (StatementTemplate.Part part : e.feeParts) {
            summe += part.cents;
        }
        assertEquals(77358L, summe);
        // Die feste Gebühr ist die letzte Zeile und bringt ihre Kategorie mit; als Kategorie für den
        // ganzen Betrag träte sie an die Stelle der Kapitalertragsteuer.
        assertEquals("Ausgaben:Bankgebühren", e.feeParts.get(3).category);
        assertEquals(99L, e.feeParts.get(3).cents);
        assertEquals("", e.feeCategory);
    }

    /**
     * Ohne Aufteilung bleibt es beim Bisherigen: die <b>ganze</b> Gebühr wird fest gelernt. Das ist der
     * Fall, für den der Rückfall gebaut wurde — eine Bank, die ihre Ordergebühr gar nicht ausweist.
     */
    @Test
    public void ohneAufteilungWirdDieGanzeGebuehrFestGelernt() {
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = "sell";
        known.netCents = 2077862L;   // Kurswert minus 799,99, also nirgends im Beleg
        known.feeCents = 79999L;
        known.feeCategory = "Ausgaben:Bankgebühren";
        StatementTemplate t = TemplateLearner.learn(StatementFixtures.scalableVerkauf(), known);
        assertEquals(79999L, t.fixedFeeCents);
        assertEquals("Ausgaben:Bankgebühren", t.fixedFeeCategory);
    }

    @Test
    public void jederTeilbetragBekommtSeineEigeneRegel() {
        PdfText text = StatementFixtures.ingDividende();
        StatementTemplate t = gelernt(text, 15873L, 873L);
        assertEquals(2, t.feeParts.size());
        assertNotNull(mit(t.feeParts, "Kapitalertragsteuer"));
        assertNotNull(mit(t.feeParts, "Solidaritätszuschlag"));
        // Und sie lesen wieder genau das, wofür sie gelernt wurden.
        assertEquals(Long.valueOf(15873L), mit(t.feeParts, "Kapitalertragsteuer").rule.readCents(text));
        assertEquals(Long.valueOf(873L), mit(t.feeParts, "Solidaritätszuschlag").rule.readCents(text));
    }

    /**
     * Die Summenregel bleibt daneben stehen. Ohne sie läse die Vorlage die Steuer gar nicht mehr —
     * und die Teile ersetzen sie nicht, denn eine Abrechnung kann Zeilen tragen, die keinen eigenen
     * Teil bekommen haben.
     */
    @Test
    public void dieSummenregelBleibtBestehen() {
        PdfText text = StatementFixtures.ingDividende();
        StatementTemplate t = gelernt(text, 15873L, 873L);
        assertEquals(Long.valueOf(16746L), t.rule(StatementTemplate.Field.FEE).readCents(text));
    }

    /** Eine einzelne Zeile ist keine Aufteilung, sondern die Summe selbst — dafür gibt es keine Teile. */
    @Test
    public void eineEinzelneZeileWirdNichtAlsTeilGelernt() {
        assertTrue(gelernt(StatementFixtures.ingDividende(), 16746L).feeParts.isEmpty());
    }

    /**
     * Gelernt wird nicht nur, <b>wo</b> ein Betrag steht, sondern auch, <b>wohin</b> er gebucht
     * gehört. Sonst müsste die Kategorie jedes Mal aus der letzten Buchung erschlossen werden — und
     * bei einem Wertpapier ohne Geschichte gäbe es dort nichts zu erschließen.
     */
    @Test
    public void dieKategorieWirdMitgelernt() {
        StatementTemplate t = gelernt(StatementFixtures.ingDividende(), 15873L, 873L);
        assertEquals("Steuern 15873", mit(t.feeParts, "Kapitalertragsteuer").category);
        assertEquals("Steuern 873", mit(t.feeParts, "Solidaritätszuschlag").category);
    }

    /**
     * Ohne Aufteilung gehört die Kategorie dem ganzen Betrag. Eine zusätzliche Teilregel dafür wäre
     * nur eine zweite Wahrheit über dieselbe Zahl.
     */
    @Test
    public void ohneAufteilungGehoertDieKategorieDemGanzenBetrag() {
        StatementTemplate t = gelernt(StatementFixtures.ingDividende(), 16746L);
        assertTrue(t.feeParts.isEmpty());
        assertEquals("Steuern 16746", t.feeCategory);
    }

    /** Und beim Anwenden kommt sie als eine Zeile über den ganzen Betrag heraus. */
    @Test
    public void dieKategorieDesGanzenBetragsWirdZuEinerZeile() {
        PdfText text = StatementFixtures.ingDividende();
        StatementTemplate.Extraction e = gelernt(text, 16746L).apply(text);
        assertEquals(1, e.feeParts.size());
        assertEquals(16746L, e.feeParts.get(0).cents);
        assertEquals("Steuern 16746", e.feeParts.get(0).category);
    }

    /** Beim Anwenden trägt jeder Teil seine Kategorie mit sich. */
    @Test
    public void angewandtTragenDieTeileIhreKategorie() {
        PdfText text = StatementFixtures.ingDividende();
        StatementTemplate.Extraction e = gelernt(text, 15873L, 873L).apply(text);
        assertEquals(2, e.feeParts.size());
        for (StatementTemplate.Part part : e.feeParts) {
            assertEquals("Steuern " + part.cents, part.category);
        }
    }

    /**
     * Eine einmal festgelegte Kategorie überlebt einen Lernvorgang, der keine hergibt — sonst wäre
     * sie nach der nächsten Abrechnung fort, und niemand käme darauf, warum.
     */
    @Test
    public void eineFestgelegteKategorieUeberlebtDasNaechsteLernen() {
        StatementTemplate alt = gelernt(StatementFixtures.ingDividende(), 15873L, 873L);
        TemplateLearner.Known ohne = new TemplateLearner.Known();
        ohne.action = "dividend";
        ohne.netCents = 73953L;
        ohne.feeCents = 16746L;
        StatementTemplate neu = TemplateLearner.learn(StatementFixtures.ingDividende(), ohne)
                .mergedOver(alt);
        assertEquals("Steuern 15873", mit(neu.feeParts, "Kapitalertragsteuer").category);
    }

    @Test
    public void angewandtKommenDieTeileEinzelnHeraus() {
        PdfText text = StatementFixtures.ingDividende();
        StatementTemplate.Extraction e = gelernt(text, 15873L, 873L).apply(text);
        assertEquals(2, e.feeParts.size());
        long summe = 0;
        for (StatementTemplate.Part part : e.feeParts) {
            summe += part.cents;
        }
        assertEquals(16746L, summe);
    }

    /**
     * Eine Abrechnung ohne Solidaritätszuschlag darf die gelernte Aufteilung nicht verkürzen: sonst
     * läse die nächste vollständige die Steuer auf zu wenige Kategorien auf, ohne dass es auffiele.
     */
    @Test
    public void eineFehlendeZeileVerkuerztDieAufteilungNicht() {
        StatementTemplate alt = gelernt(StatementFixtures.ingDividende(), 15873L, 873L);
        PdfText ohneSoli = StatementFixtures.ingDividendeOhneSteuer();
        StatementTemplate neu = gelernt(ohneSoli, 15873L, 873L).mergedOver(alt);
        assertEquals(2, neu.feeParts.size());
        assertNotNull(mit(neu.feeParts, "Kapitalertragsteuer"));
        assertNotNull(mit(neu.feeParts, "Solidaritätszuschlag"));
    }

    /** Was in der Vorlage steht, muss das Speichern überstehen — sonst wäre es beim nächsten Start fort. */
    @Test
    public void teilregelnUeberstehenSpeichernUndLaden() {
        StatementTemplate t = gelernt(StatementFixtures.ingDividende(), 15873L, 873L);
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        store.saveAll(Collections.singletonList(t));

        StatementTemplate zurueck = new StatementTemplates(ctx).all().get(0);
        assertEquals(2, zurueck.feeParts.size());
        assertNotNull(mit(zurueck.feeParts, "Kapitalertragsteuer"));
        assertTrue("die Vorlage kommt unverändert zurück", t.sameAs(zurueck));
    }
}
