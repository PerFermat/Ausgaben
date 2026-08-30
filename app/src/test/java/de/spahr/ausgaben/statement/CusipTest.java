package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/** Die CUSIP als Kennung für US-/kanadische Broker, die keine ISIN drucken. */
public class CusipTest {

    @Test
    public void echteCusipsWerdenAlsGültigErkannt() {
        assertTrue(Cusip.isValid("037833100"));   // Apple
        assertTrue(Cusip.isValid("78464A870"));   // SPDR Biotech ETF (XBI)
    }

    @Test
    public void eineVerfälschtePrüfzifferFälltDurch() {
        assertFalse(Cusip.isValid("037833101"));
        assertFalse(Cusip.isValid("78464A871"));
    }

    @Test
    public void formfehlerFallenDurch() {
        assertFalse(Cusip.isValid("03783310"));    // zu kurz
        assertFalse(Cusip.isValid("0378331000"));  // zu lang
        assertFalse(Cusip.isValid(null));
        assertFalse(Cusip.isValid(""));
    }

    @Test
    public void kleinschreibungStörtNicht() {
        assertTrue(Cusip.isValid("78464a870"));
    }

    @Test
    public void cusipWirdAusEinerUsAbrechnungGelesen() {
        PdfText t = StatementFixtures.of(
                "YOU BOUGHT XBI 78464A870 06/29/22 07/01/22 MARGIN 5 $74.33000");
        assertEquals("78464A870", Cusip.single(t));
        assertEquals("78464A870", StatementScan.isin(t));   // ohne ISIN springt der Fallback ein
    }

    /** Sammelabrechnung: mehrere Papiere in einem Dokument – dann lieber keines vorbelegen. */
    @Test
    public void mehrereCusipsLiefernKeineEinzelne() {
        PdfText t = StatementFixtures.of("037833100", "78464A870");
        List<String> all = Cusip.findAll(t);
        assertEquals(2, all.size());
        assertNull(Cusip.single(t));
    }
}
