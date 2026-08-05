package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Die Merkliste der noch hochzuladenden Belege führt seit dem Wegfall des Jahres im Dateinamen den
 * Jahresordner mit: {@code <jahr>|<datei>}. Einträge aus älteren Versionen stehen ohne Strich darin.
 */
public class ReceiptsEntryTest {

    @Test
    public void entry_withYear() {
        assertEquals("abc_p1.jpg", Receipts.entryFile("2026|abc_p1.jpg"));
        assertEquals(2026, Receipts.entryYear("2026|abc_p1.jpg"));
    }

    @Test
    public void entry_legacyWithoutYearFallsBackToTheName() {
        // Altbestand: kein Strich, dafür trägt der Dateiname noch das Jahr.
        assertEquals("2025_abc.jpg", Receipts.entryFile("2025_abc.jpg"));
        assertEquals(2025, Receipts.entryYear("2025_abc.jpg"));
    }

    @Test
    public void entry_unknownYear() {
        assertEquals(-1, Receipts.entryYear("abc_p1.jpg"));
        assertEquals(-1, Receipts.entryYear("keinJahr|abc_p1.jpg"));
    }
}
