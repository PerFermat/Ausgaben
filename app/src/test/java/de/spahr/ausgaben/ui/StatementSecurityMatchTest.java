package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.db.Security;
import de.spahr.ausgaben.pdf.PdfText;

/**
 * Das Wertpapier finden, wenn die Abrechnung keine ISIN trägt.
 *
 * <p>Amerikanische Belege führen CUSIP und Kürzel statt einer ISIN — in 0 von 11 US-Dokumenten eines
 * Bestands aus 132 Banken stand eine. Gesucht wird deshalb umgekehrt: welches der <b>eigenen</b>
 * Wertpapiere kommt im Text vor? Kandidaten sind allein die eigenen Bestände, deshalb kann auch ein
 * dreibuchstabiges Kürzel nicht wild um sich greifen.</p>
 */
public class StatementSecurityMatchTest {

    private static Security wertpapier(String kmyId, String name, String symbol, String ident) {
        Security s = new Security();
        s.depot = "Depot";
        s.kmyId = kmyId;
        s.name = name;
        s.symbol = symbol;
        s.isin = ident;
        return s;
    }

    private static PdfText usKauf() {
        return PdfText.fromLines(String.join("\n",
                "We are pleased to confirm the following transaction",
                "ACTION SYMBOL CUSIP DATE DATE TYPE QUANTITY PRICE",
                "YOU BOUGHT XBI 78464A870 06/29/22 07/01/22 MARGIN 5 $74.33000",
                "SPDR SER TR S&P BIOTECH ETF PRINCIPAL $371.65"));
    }

    @Test
    public void ueberDasKuerzelGefunden() {
        List<Security> depot = Arrays.asList(
                wertpapier("E1", "SPDR S&P Biotech ETF", "XBI", ""),
                wertpapier("E2", "Vanguard All-World", "VWRL", "IE00B3RBWM25"));
        assertEquals("E1", StatementImport.namedIn(depot, usKauf()).kmyId);
    }

    @Test
    public void ueberDieCusipImIdentifikationsfeld() {
        // In KMyMoney ist die Identifikation freier Text – bei einem US-Papier steht dort die CUSIP.
        List<Security> depot = Collections.singletonList(
                wertpapier("E1", "SPDR S&P Biotech ETF", "", "78464A870"));
        assertEquals("E1", StatementImport.namedIn(depot, usKauf()).kmyId);
    }

    @Test
    public void einFremdesKuerzelFindetNichts() {
        List<Security> depot = Collections.singletonList(
                wertpapier("E2", "Vanguard All-World", "VWRL", "IE00B3RBWM25"));
        assertNull(StatementImport.namedIn(depot, usKauf()));
    }

    /** Passen zwei Wertpapiere, wird nicht geraten — die Maske fragt dann. */
    @Test
    public void beiMehrdeutigkeitLieberNichts() {
        List<Security> depot = Arrays.asList(
                wertpapier("E1", "SPDR S&P Biotech ETF", "XBI", ""),
                wertpapier("E3", "Irgendwas anderes", "", "78464A870"));
        assertNull(StatementImport.namedIn(depot, usKauf()));
    }

    /**
     * Zu kurze Kennungen werden gar nicht erst gesucht: ein Kürzel wie „F" oder „GM" käme in jedem Text
     * vor, und eine leere Identifikation erst recht.
     */
    @Test
    public void zuKurzesWirdNichtGesucht() {
        List<Security> depot = Arrays.asList(
                wertpapier("E4", "Ford", "F", ""),
                wertpapier("E5", "Ohne alles", "", ""));
        assertNull(StatementImport.namedIn(depot, usKauf()));
    }

    /** Ein Kürzel mit Punkt wie „XAD1.DE" findet sich auch, wenn die Bank anders trennt. */
    @Test
    public void kuerzelMitTrennzeichen() {
        List<Security> depot = Collections.singletonList(
                wertpapier("E6", "DB ETC Gold", "XAD1.DE", ""));
        PdfText beleg = PdfText.fromLines("Wertpapier XAD1.DE Kauf\nKurswert EUR 100,00");
        assertEquals("E6", StatementImport.namedIn(depot, beleg).kmyId);
    }
}
