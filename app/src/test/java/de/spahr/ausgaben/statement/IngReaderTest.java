package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.statement.bank.BankReaders;
import de.spahr.ausgaben.statement.bank.IngReader;

/**
 * Der fest programmierte Leser für die ING.
 *
 * <p>Der Anspruch ist hier ein anderer als bei der gelernten Ankerlogik: dort zählt, wie weit man ohne
 * jede Kenntnis der Bank kommt, hier müssen <b>alle</b> Werte stimmen. Ein Leser, der nur die Hälfte
 * liefert, lohnt den Quelltext nicht — dafür gibt es die Vorlagen.</p>
 */
public class IngReaderTest {

    private final IngReader leser = new IngReader();

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    private StatementTemplate.Extraction lies(PdfText text) {
        assertTrue("erkennt den Beleg nicht", leser.matches(text));
        StatementTemplate.Extraction e = new StatementTemplate.Extraction();
        leser.read(text, e);
        return e;
    }

    @Test
    public void kaufAusSparplan() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingKauf());

        assertEquals("buy", e.action);
        // Der Ausführungstag – nicht die Valuta (19.08., nur die Wertstellung des Geldes) und nicht
        // das Druckdatum (18.08.). Ein Kauf gilt an dem Tag, an dem er ausgeführt wurde.
        assertEquals(tag(2026, 8, 17), e.dateMillis);
        assertEquals(6.09607, e.shares, 1e-9);
        assertEquals(164.04, e.price, 1e-9);
        assertEquals(Long.valueOf(100_000L), e.netCents);
        // Kurswert und Endbetrag sind gleich: es fiel nichts an. Das ist eine Auskunft, kein Nichtwissen.
        assertEquals(Long.valueOf(0L), e.feeCents);
    }

    @Test
    public void kaufMitProvision() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingKaufMitProvision());

        assertEquals("buy", e.action);
        // Weder Valuta noch Zahltag stehen da – dann ist der Ausführungstag der richtige.
        assertEquals(tag(2026, 9, 19), e.dateMillis);
        assertEquals(3.12345, e.shares, 1e-9);
        assertEquals(170.50, e.price, 1e-9);
        assertEquals(Long.valueOf(490L), e.feeCents);
        assertEquals(Long.valueOf(53_753L), e.netCents);
    }

    /**
     * Regression: Die Kosten entstehen als Differenz zwischen Kurswert und Endbetrag, und diese
     * Differenz hat beim Verkauf das andere Vorzeichen als beim Kauf. Gerechnet wurde aber
     * {@code |Endbetrag − Kurswert| − Stückzinsen} — beim Verkauf einer Anleihe ist das
     * {@code (Stückzinsen − Kosten) − Stückzinsen}, also die <b>negative</b> Kostensumme. In der Maske
     * stand damit eine Gebühr von −30,00 €, und die Gesamtsumme ging um den doppelten Betrag daneben.
     */
    @Test
    public void verkaufMitStueckzinsenHatPositiveKosten() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingVerkaufMitStueckzinsen());

        assertEquals("sell", e.action);
        // Provision 20,00 + Kapitalertragsteuer 10,00; die Stückzinsen sind Preis, keine Kosten.
        assertEquals(Long.valueOf(3_000L), e.feeCents);
        assertEquals(Long.valueOf(1_002_000L), e.netCents);
    }

    /** Beim Kauf kommen die Kosten obendrauf – diese Richtung stimmte schon vorher. */
    @Test
    public void kaufMitStueckzinsenRechnetInDieAndereRichtung() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingKaufMitStueckzinsen());

        assertEquals("buy", e.action);
        assertEquals(Long.valueOf(3_000L), e.feeCents);
        assertEquals(Long.valueOf(1_008_000L), e.netCents);
    }

    /** Dollar-Papier: der Bruttobetrag steht in USD, gebucht wird der umgerechnete Euro-Betrag. */
    @Test
    public void dividendeInFremdwaehrung() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingDividende());

        assertEquals("dividend", e.action);
        assertEquals(tag(2026, 8, 17), e.dateMillis);
        assertEquals(1839.80185, e.shares, 1e-9);
        assertNull("eine Dividende hat keinen Stückpreis", e.price);
        assertEquals(Long.valueOf(90_699L), e.grossCents);
        // Kapitalertragsteuer 158,73 + Solidaritätszuschlag 8,73.
        assertEquals(Long.valueOf(16_746L), e.feeCents);
        assertEquals(Long.valueOf(73_953L), e.netCents);
        // Die Rechnung muss aufgehen, sonst meldet die Maske einen Widerspruch.
        assertEquals(e.netCents.longValue(), e.grossCents - e.feeCents);
    }

    /**
     * Die Steuerzeilen kommen zusätzlich <b>einzeln</b> zurück, in der Reihenfolge des Dokuments und
     * ohne Beschriftung.
     *
     * <p>Ohne Beschriftung mit Absicht: welche Kategorie zu welcher Steuerart gehört, weiß nur der
     * Nutzer, und für diese Bank gibt es keine Vorlage, in der es stehen könnte. Zugeordnet wird
     * deshalb der Reihe nach — und die Reihe ist die des Belegs.</p>
     */
    @Test
    public void dieSteuerzeilenKommenEinzelnUndInDerReihenfolgeDesBelegs() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingDividende());

        assertEquals(2, e.feeParts.size());
        assertEquals(15_873L, e.feeParts.get(0).cents);   // Kapitalertragsteuer steht oben
        assertEquals(873L, e.feeParts.get(1).cents);      // Solidaritätszuschlag darunter
        assertEquals("", e.feeParts.get(0).label);
        long summe = 0;
        for (StatementTemplate.Part part : e.feeParts) {
            summe += part.cents;
        }
        assertEquals("die Aufteilung ergibt zusammen die alte Steuersumme",
                e.feeCents.longValue(), summe);
    }

    /** Ohne abgezogene Steuer gibt es auch nichts aufzuteilen. */
    @Test
    public void ohneSteuerGibtEsKeineTeile() {
        assertEquals(0, lies(StatementFixtures.ingDividendeOhneSteuer()).feeParts.size());
    }

    /** Fällt die Valuta mit dem Zahltag zusammen, druckt die Bank die Zeile nicht. */
    @Test
    public void dividendeOhneValutaNimmtDenZahltag() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingDividendeOhneValuta());

        assertEquals(tag(2026, 8, 17), e.dateMillis);
        assertEquals(Long.valueOf(73_953L), e.netCents);
    }

    /**
     * Innerhalb des Freibetrags wird nichts abgezogen. Die Steuer ist dann <b>0</b> und nicht unbekannt —
     * genau hier hat die App schon einmal eine gerechnete Steuer gezeigt, die nirgends stand.
     */
    @Test
    public void dividendeImFreibetragHatKeineSteuer() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingDividendeOhneSteuer());

        assertEquals("dividend", e.action);
        assertEquals(tag(2026, 3, 5), e.dateMillis);
        assertEquals(215.44908, e.shares, 1e-9);
        assertEquals(Long.valueOf(0L), e.feeCents);
        assertEquals(Long.valueOf(6_316L), e.grossCents);
        assertEquals(Long.valueOf(6_316L), e.netCents);
    }

    /** Die zweite Seite rechnet den Freibetrag durch; keine ihrer Zeilen ist ein Abzug. */
    @Test
    public void dieFreibetragsrechnungWirdNichtAlsSteuerGelesen() {
        StatementTemplate.Extraction e = lies(StatementFixtures.ingDividendeOhneSteuer());

        assertEquals(Long.valueOf(0L), e.feeCents);
    }

    @Test
    public void inDerListeGefunden() {
        assertNotNull(BankReaders.find(StatementFixtures.ingKauf()));
        assertEquals("ing", BankReaders.find(StatementFixtures.ingKauf()).id());
    }
}
