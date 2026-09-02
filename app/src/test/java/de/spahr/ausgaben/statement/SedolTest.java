package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/** Die SEDOL als Kennung für britische/irische Broker, die keine ISIN drucken. */
public class SedolTest {

    @Test
    public void echteSedolsWerdenAlsGültigErkannt() {
        assertTrue(Sedol.isValid("0798059"));    // BP
        assertTrue(Sedol.isValid("BH4HKS3"));    // Vodafone
    }

    @Test
    public void eineVerfälschtePrüfzifferFälltDurch() {
        assertFalse(Sedol.isValid("0798050"));
        assertFalse(Sedol.isValid("BH4HKS4"));
    }

    @Test
    public void formfehlerFallenDurch() {
        assertFalse(Sedol.isValid("079805"));     // zu kurz
        assertFalse(Sedol.isValid("07980599"));   // zu lang
        assertFalse(Sedol.isValid(null));
        assertFalse(Sedol.isValid(""));
    }

    @Test
    public void kleinschreibungStörtNicht() {
        assertTrue(Sedol.isValid("bh4hks3"));
    }

    @Test
    public void sedolWirdAusEinerUkAbrechnungGelesen() {
        PdfText t = StatementFixtures.of("Bought VOD.L SEDOL BH4HKS3 price 74.5p");
        assertEquals("BH4HKS3", Sedol.single(t));
        assertEquals("BH4HKS3", StatementScan.isin(t));   // ohne ISIN/CUSIP springt der Fallback ein
    }

    /** Sammelabrechnung: mehrere Papiere in einem Dokument – dann lieber keines vorbelegen. */
    @Test
    public void mehrereSedolsLiefernKeineEinzelne() {
        PdfText t = StatementFixtures.of("0798059", "BH4HKS3");
        List<String> all = Sedol.findAll(t);
        assertEquals(2, all.size());
        assertNull(Sedol.single(t));
    }

    /**
     * Vokale kommen in einer SEDOL nicht vor. Die Einschränkung ist keine Kosmetik: sie nimmt der
     * Prüfziffer einen guten Teil ihrer Arbeit ab. Vorher bestand jede siebenstellige Referenznummer aus
     * Buchstaben und Ziffern die Prüfung mit rund einem Zehntel Wahrscheinlichkeit — und ein Treffer
     * wird über {@code StatementTemplates.rememberSecurity} dauerhaft einem Wertpapier zugeordnet.
     */
    @Test
    public void einKuerzelMitVokalIstKeineSedol() {
        // Prüfziffer stimmt (nachgerechnet), der Vokal schließt es trotzdem aus.
        assertFalse(Sedol.isValid("BEAHKS6"));
        assertNull(Sedol.single(StatementFixtures.of("Referenz BEAHKS6 vom 17.08.2026")));
    }

    /** Und im Text wird so ein Wort erst gar nicht als Kandidat aufgegriffen. */
    @Test
    public void auftragsnummernMitVokalenWerdenNichtGelesen() {
        assertEquals(0, Sedol.findAll(StatementFixtures.of("Auftrag ORDER14 Auftrag REIHE46")).size());
    }
}
