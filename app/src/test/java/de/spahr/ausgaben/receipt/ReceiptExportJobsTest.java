package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import de.spahr.ausgaben.db.Booking;

/**
 * Welche Belege in die ausgegebene Datei kommen ({@link ReceiptExportJobs}).
 */
public class ReceiptExportJobsTest {

    private static long am(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag, 12, 0, 0);
        return c.getTimeInMillis();
    }

    private static Booking buchung(long id, String payee, long cents, String note) {
        Booking b = new Booking();
        b.id = id;
        b.payee = payee;
        b.amountCents = cents;
        b.note = note;
        b.createdAt = am(2026, 8, 16);
        return b;
    }

    private static Booking umbuchung(long id, boolean einnahme, String gruppe, String note) {
        Booking b = buchung(id, "Sparbuch", 5000, note);
        b.isTransfer = true;
        b.isIncome = einnahme;
        b.transferGroup = gruppe;
        return b;
    }

    @Test
    public void buchungenOhneBelegFallenWeg() {
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                buchung(1, "Bäcker", 250, "Frühstück"),
                buchung(2, "Bäcker", 250, "Mittag BELEG: abc"),
                buchung(3, "Bäcker", 250, null)));
        assertEquals(1, jobs.size());
        assertEquals("abc", jobs.get(0).tagName);
        assertEquals(2, jobs.get(0).bookingId);
    }

    @Test
    public void fotoUndPdfBekommenIhreEndung() {
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                buchung(1, "A", 100, "BELEG: foto"),
                buchung(2, "B", 100, "BELEG (PDF): dok")));
        assertEquals(NoteReceipt.JPG, jobs.get(0).ext);
        assertEquals(NoteReceipt.PDF, jobs.get(1).ext);
    }

    @Test
    public void eineUmbuchungLiefertNurEinenBeleg() {
        // Beide Seiten tragen dieselbe Notiz und damit denselben Beleg.
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                umbuchung(10, true, "g1", "BELEG: abc"),
                umbuchung(11, false, "g1", "BELEG: abc")));
        assertEquals(1, jobs.size());
        // Und zwar die abgebende Seite – ihr Betrag und ihre Nummer stehen im Dateinamen.
        assertEquals(11, jobs.get(0).bookingId);
    }

    @Test
    public void trifftDerFilterNurEineSeiteDerUmbuchungGiltDiese() {
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(
                java.util.Collections.singletonList(umbuchung(10, true, "g1", "BELEG: abc")));
        assertEquals(1, jobs.size());
        assertEquals(10, jobs.get(0).bookingId);
    }

    @Test
    public void derselbeBelegKommtNurEinmal() {
        // Zwei getrennte Buchungen, die auf dieselbe Datei verweisen (etwa kopierte Notiz).
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                buchung(1, "A", 100, "BELEG: abc"),
                buchung(2, "B", 200, "BELEG: abc")));
        assertEquals(1, jobs.size());
        assertEquals(1, jobs.get(0).bookingId);
    }

    @Test
    public void dieReihenfolgeDerListeBleibt() {
        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                buchung(1, "A", 100, "BELEG: eins"),
                umbuchung(2, true, "g1", "BELEG: zwei"),
                buchung(3, "C", 300, "BELEG: drei"),
                umbuchung(4, false, "g1", "BELEG: zwei")));
        assertEquals(3, jobs.size());
        assertEquals("eins", jobs.get(0).tagName);
        // Die Umbuchung steht an der Stelle ihrer ersten Zeile, gewählt ist aber die abgebende.
        assertEquals("zwei", jobs.get(1).tagName);
        assertEquals(4, jobs.get(1).bookingId);
        assertEquals("drei", jobs.get(2).tagName);
    }

    @Test
    public void dasJahrKommtAusDemBuchungsdatum() {
        Booking b = buchung(1, "A", 100, "BELEG: abc");
        b.createdAt = am(2024, 3, 7);
        assertEquals(2024, ReceiptExportJobs.collect(
                java.util.Collections.singletonList(b)).get(0).year);
    }

    @Test
    public void leereEingabeIstKeinFehler() {
        assertTrue(ReceiptExportJobs.collect(null).isEmpty());
        assertTrue(ReceiptExportJobs.collect(new ArrayList<>()).isEmpty());
    }

    /** Ist eine Depot-Bewegung bekannt, trägt der Auftrag Art und Wertpapier. */
    @Test
    public void eineDepotBuchungBringtArtUndPapierMit() {
        java.util.Map<Long, String[]> wp = new java.util.HashMap<>();
        wp.put(2L, new String[]{"Kauf", "Vanguard FTSE All-World"});

        List<ReceiptExportJobs.Job> jobs = ReceiptExportJobs.collect(Arrays.asList(
                buchung(1, "Bäcker", 250, "BELEG: abc"),
                buchung(2, "Vanguard FTSE All-World", 50000, "BELEG (PDF): dok")), wp);

        assertEquals(2, jobs.size());
        assertFalse("die Bäckerei ist keine Bewegung", jobs.get(0).istWertpapier());
        assertTrue(jobs.get(1).istWertpapier());
        assertEquals("Kauf", jobs.get(1).action);
        assertEquals("Vanguard FTSE All-World", jobs.get(1).securityName);
    }

    /** Ohne Zuordnung bleibt alles beim Empfänger-Schema – auch wenn die Karte leer oder {@code null} ist. */
    @Test
    public void ohneZuordnungBleibtEsBeimEmpfaenger() {
        List<Booking> eine = java.util.Collections.singletonList(
                buchung(1, "Bäcker", 250, "BELEG: abc"));
        assertFalse(ReceiptExportJobs.collect(eine, null).get(0).istWertpapier());
        assertFalse(ReceiptExportJobs.collect(eine, new java.util.HashMap<>()).get(0).istWertpapier());
    }
}
