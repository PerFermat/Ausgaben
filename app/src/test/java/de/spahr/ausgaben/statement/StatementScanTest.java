package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Was ohne gelernte Vorlage erkannt wird: die ISIN (mit Prüfziffer) und ein Vorschlag für die Aktion.
 * Mehr steht bewusst nicht im Code — alles Bankspezifische lernt die Vorlage.
 */
public class StatementScanTest {

    // ---- ISIN ----

    @Test
    public void echteIsinsWerdenAlsGültigErkannt() {
        assertTrue(Isin.isValid("IE00B3RBWM25"));   // Vanguard FTSE All-World
        assertTrue(Isin.isValid("IE00B9CQXS71"));   // SPDR Global Dividend Aristocrats
        assertTrue(Isin.isValid("US0378331005"));   // Apple
        assertTrue(Isin.isValid("DE0005557508"));   // Telekom
    }

    @Test
    public void eineVerfälschtePrüfzifferFälltDurch() {
        assertFalse(Isin.isValid("IE00B3RBWM26"));
        assertFalse(Isin.isValid("US0378331006"));
    }

    @Test
    public void formfehlerFallenDurch() {
        assertFalse(Isin.isValid("IE00B3RBWM2"));    // zu kurz
        assertFalse(Isin.isValid("IE00B3RBWM2XY"));  // zu lang
        assertFalse(Isin.isValid("1E00B3RBWM25"));   // Länderkennung keine Buchstaben
        assertFalse(Isin.isValid("IE00B3RBWM2X"));   // Prüfstelle keine Ziffer
        assertFalse(Isin.isValid(null));
        assertFalse(Isin.isValid(""));
    }

    @Test
    public void kleinschreibungStörtNicht() {
        assertTrue(Isin.isValid("ie00b3rbwm25"));
    }

    @Test
    public void isinWirdAusDerAbrechnungGelesen() {
        assertEquals("IE00B3RBWM25", StatementScan.isin(StatementFixtures.ingKauf()));
        assertEquals("IE00B9CQXS71", StatementScan.isin(StatementFixtures.ingDividende()));
    }

    /**
     * Die WKN steht in derselben Zeile und sieht einer ISIN nicht ähnlich genug – aber die Ordernummer
     * und die IBAN sollen ebenfalls nicht als ISIN durchgehen.
     */
    @Test
    public void andereNummernWerdenNichtFürEineIsinGehalten() {
        PdfText t = StatementFixtures.of(
                "Ordernummer                 123456789.001",
                "Abrechnungs-IBAN            DE81 5001 0517 5556 7477 21",
                "Wertpapierkennnummer        A1JX52");
        assertTrue(Isin.findAll(t).isEmpty());
    }

    /** Sammelabrechnung: mehrere Wertpapiere in einem Dokument – dann lieber keines vorbelegen. */
    @Test
    public void mehrereIsinsLiefernKeineEinzelne() {
        PdfText t = StatementFixtures.of(
                "ISIN IE00B3RBWM25",
                "ISIN IE00B9CQXS71");
        List<String> all = Isin.findAll(t);
        assertEquals(2, all.size());
        assertNull(StatementScan.isin(t));
    }

    @Test
    public void dieselbeIsinZweimalBleibtEindeutig() {
        PdfText t = StatementFixtures.of(
                "ISIN (WKN) IE00B3RBWM25 (A1JX52)",
                "Wertpapier IE00B3RBWM25 wurde gekauft");
        assertEquals("IE00B3RBWM25", StatementScan.isin(t));
    }

    // ---- Aktion ----

    @Test
    public void aktionAusDenEchtenAbrechnungen() {
        assertEquals(StatementScan.BUY, StatementScan.guessAction(StatementFixtures.ingKauf()));
        assertEquals(StatementScan.DIVIDEND,
                StatementScan.guessAction(StatementFixtures.ingDividende()));
    }

    /**
     * Der Stolperstein: „Verkaufsabrechnung" enthält „kauf". Ohne die Prüfreihenfolge Dividende →
     * Verkauf → Kauf würde jeder Verkauf als Kauf gelesen.
     */
    @Test
    public void verkaufWirdNichtFürEinenKaufGehalten() {
        assertEquals(StatementScan.SELL, StatementScan.guessAction(
                StatementFixtures.of("Wertpapierabrechnung Verkauf")));
        assertEquals(StatementScan.SELL, StatementScan.guessAction(
                StatementFixtures.of("Verkaufsabrechnung", "Nominale 5 Stück")));
    }

    @Test
    public void englischeAbrechnungenWerdenAuchVorgeschlagen() {
        assertEquals(StatementScan.BUY,
                StatementScan.guessAction(StatementFixtures.of("Trade Confirmation - Purchase")));
        assertEquals(StatementScan.SELL,
                StatementScan.guessAction(StatementFixtures.of("Trade Confirmation - Sale")));
        assertEquals(StatementScan.DIVIDEND,
                StatementScan.guessAction(StatementFixtures.of("Dividend Statement")));
    }

    /** Eine Wiederanlage nennt Ausschüttung und Kauf – die Dividende ist das speziellere Wort. */
    @Test
    public void dividendeSchlägtKauf() {
        assertEquals(StatementScan.DIVIDEND, StatementScan.guessAction(
                StatementFixtures.of("Ausschüttung mit Wiederanlage", "Kauf von 3 Stück")));
    }

    @Test
    public void ohneTreffendesWortGibtEsKeinenVorschlag() {
        assertNull(StatementScan.guessAction(StatementFixtures.of("Depotauszug", "Bestand zum 31.12.")));
        assertNull(StatementScan.guessAction(null));
    }
}
