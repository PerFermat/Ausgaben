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
}
