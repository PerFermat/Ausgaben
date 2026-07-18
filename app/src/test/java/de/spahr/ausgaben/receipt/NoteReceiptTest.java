package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Charakterisierungstests für {@link NoteReceipt} – der Beleg-Verweis im Notizfeld (GPS bleibt unberührt). */
public class NoteReceiptTest {

    @Test
    public void fileName_readsTag() {
        assertEquals("2025_ab12.jpg", NoteReceipt.fileName("Kaffee BELEG: 2025_ab12.jpg"));
    }

    @Test
    public void fileName_absentOrNull() {
        assertNull(NoteReceipt.fileName("nur eine Notiz"));
        assertNull(NoteReceipt.fileName(null));
    }

    @Test
    public void withFileName_appendsAndReplaces() {
        String n1 = NoteReceipt.withFileName("Kaffee", "2025_a.jpg");
        assertEquals("Kaffee BELEG: 2025_a.jpg", n1);
        // Ersetzen: kein doppelter Tag.
        String n2 = NoteReceipt.withFileName(n1, "2025_b.jpg");
        assertEquals("Kaffee BELEG: 2025_b.jpg", n2);
    }

    @Test
    public void withFileName_keepsGpsTag() {
        String note = "Kaffee GPS: 48.1, 11.5";
        String out = NoteReceipt.withFileName(note, "2025_a.jpg");
        assertTrue(out.contains("GPS: 48.1, 11.5"));
        assertEquals("2025_a.jpg", NoteReceipt.fileName(out));
    }

    @Test
    public void strip_removesOnlyBeleg() {
        assertEquals("Kaffee GPS: 48.1, 11.5",
                NoteReceipt.strip("Kaffee GPS: 48.1, 11.5 BELEG: 2025_a.jpg"));
        assertEquals("Kaffee", NoteReceipt.strip("BELEG: 2025_a.jpg Kaffee"));
        assertEquals("", NoteReceipt.strip("BELEG: 2025_a.jpg"));
    }

    @Test
    public void newFileName_yearPrefixAndRoundTrip() {
        String f = NoteReceipt.newFileName(2025);
        assertTrue(f.startsWith("2025_"));
        assertTrue(f.endsWith(".jpg"));
        assertEquals(2025, NoteReceipt.yearOf(f));
    }

    @Test
    public void yearOf_invalid() {
        assertEquals(-1, NoteReceipt.yearOf("noprefix.jpg"));
        assertEquals(-1, NoteReceipt.yearOf(null));
    }
}
