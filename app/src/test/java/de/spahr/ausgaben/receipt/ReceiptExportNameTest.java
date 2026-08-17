package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Die Namen der ausgegebenen Belege ({@link ReceiptExportName}).
 */
public class ReceiptExportNameTest {

    /** 16. August 2026, mittags – die Uhrzeit spielt für den Namen keine Rolle. */
    private static long am(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.clear();
        c.set(jahr, monat - 1, tag, 12, 0, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void dasBeispielAusDemAuftrag() {
        assertEquals("2026-08-16_Bäckerei-Mayer_8_23_1487_p1.jpg",
                ReceiptExportName.of(am(2026, 8, 16), "Bäckerei Mayer", -823, 1487, "abc_p1.jpg"));
    }

    @Test
    public void derBetragTraegtKeinVorzeichen() {
        // Ausgabe wie Einnahme sehen gleich aus; das Vorzeichen sagt im Dateinamen nichts.
        assertEquals("8_23", ReceiptExportName.amount(-823));
        assertEquals("8_23", ReceiptExportName.amount(823));
        assertEquals("0_50", ReceiptExportName.amount(-50));
        assertEquals("0_00", ReceiptExportName.amount(0));
        // Keine Tausendertrennung – die käme im Dateinamen nur in die Quere.
        assertEquals("1234_56", ReceiptExportName.amount(123456));
    }

    @Test
    public void derEmpfaengerWirdDateinamentauglich() {
        assertEquals("Bäckerei-Mayer", ReceiptExportName.payee("Bäckerei Mayer"));
        // Schrägstrich und Doppelpunkt zerbrechen sonst Pfade bzw. Windows.
        assertEquals("Müller-Sohn", ReceiptExportName.payee("Müller/Sohn"));
        assertEquals("Konto-1", ReceiptExportName.payee("Konto: 1"));
        // Mehrere Trenner hintereinander werden einer, Ränder fallen weg.
        assertEquals("A-B", ReceiptExportName.payee("  A   //  B  "));
        // Der Unterstrich ist unser eigenes Trennzeichen und darf im Namensteil nicht vorkommen.
        assertEquals("Netto-Marken-Discount", ReceiptExportName.payee("Netto_Marken_Discount"));
    }

    @Test
    public void ohneEmpfaengerStehtEinErsatzname() {
        assertEquals("ohne-Empfaenger", ReceiptExportName.payee(null));
        assertEquals("ohne-Empfaenger", ReceiptExportName.payee("   "));
        assertEquals("ohne-Empfaenger", ReceiptExportName.payee("///"));
    }

    @Test
    public void einLangerNameWirdGekappt() {
        String lang = ReceiptExportName.payee(new String(new char[200]).replace('\0', 'x'));
        assertTrue(lang.length() <= 60);
    }

    @Test
    public void seiteUndEndungKommenAusDerQuelldatei() {
        assertTrue(ReceiptExportName.of(am(2026, 1, 2), "A", 100, 7, "abc_p3.jpg").endsWith("_7_p3.jpg"));
        // Ein PDF-Beleg bleibt ein PDF.
        assertTrue(ReceiptExportName.of(am(2026, 1, 2), "A", 100, 7, "abc_p2.pdf").endsWith("_7_p2.pdf"));
        // Altbeleg ohne Seitenzusatz zählt als Seite 1, damit alle Namen gleich aussehen.
        assertTrue(ReceiptExportName.of(am(2026, 1, 2), "A", 100, 7, "2026_abc.jpg").endsWith("_7_p1.jpg"));
    }

    @Test
    public void dasDatumSortiertVonSelbst() {
        assertEquals("2026-01-02", ReceiptExportName.date(am(2026, 1, 2)));
        assertEquals("2026-12-31", ReceiptExportName.date(am(2026, 12, 31)));
    }
}
