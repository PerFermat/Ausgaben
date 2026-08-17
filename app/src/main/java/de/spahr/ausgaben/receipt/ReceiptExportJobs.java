package de.spahr.ausgaben.receipt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.db.Booking;

/**
 * Welche Belege zu einer gefilterten Buchungsliste gehören – die ganze Entscheidungsarbeit des
 * Beleg-Exports, ohne Android und damit unter JUnit prüfbar.
 *
 * <p>Zwei Fälle machen die Sache mehr als eine Schleife: Buchungen <b>ohne</b> Beleg fallen weg, und
 * eine <b>Umbuchung</b> besteht aus zwei Zeilen, die sich einen Beleg teilen – der soll nur einmal in
 * die Datei, benannt nach der abgebenden Seite.</p>
 */
public final class ReceiptExportJobs {

    /** Ein Beleg samt allem, was der Packer für seinen Namen braucht. */
    public static final class Job {
        /** Der Verweis aus der Notiz – damit findet {@link ReceiptPages#find} die Seiten. */
        public final String tagName;
        /** {@link NoteReceipt#JPG} oder {@link NoteReceipt#PDF}. */
        public final String ext;
        /** Jahresordner auf dem Server, aus dem Buchungsdatum. */
        public final int year;
        public final long createdAt;
        public final String payee;
        public final long amountCents;
        public final long bookingId;

        Job(String tagName, String ext, int year, long createdAt, String payee, long amountCents,
            long bookingId) {
            this.tagName = tagName;
            this.ext = ext;
            this.year = year;
            this.createdAt = createdAt;
            this.payee = payee;
            this.amountCents = amountCents;
            this.bookingId = bookingId;
        }
    }

    private ReceiptExportJobs() {
    }

    /**
     * Die zu packenden Belege, in der Reihenfolge der übergebenen Liste.
     *
     * @param bookings die gefilterten Buchungen
     */
    public static List<Job> collect(List<Booking> bookings) {
        List<Job> out = new ArrayList<>();
        if (bookings == null) {
            return out;
        }
        Set<String> seenTags = new HashSet<>();
        for (Booking b : pickOnePerTransfer(bookings)) {
            if (b == null || b.note == null) {
                continue;
            }
            String pdf = NoteReceipt.pdfName(b.note);
            String tag = pdf != null ? pdf : NoteReceipt.fileName(b.note);
            if (tag == null) {
                continue; // Buchung ohne Beleg
            }
            // Zwei Buchungen können denselben Beleg nennen (etwa eine von Hand kopierte Notiz);
            // in der Datei soll er trotzdem nur einmal liegen.
            if (!seenTags.add(tag.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(new Job(tag, pdf != null ? NoteReceipt.PDF : NoteReceipt.JPG,
                    yearOf(b.createdAt), b.createdAt, b.payee, b.amountCents, b.id));
        }
        return out;
    }

    /**
     * Von einer Umbuchung bleibt nur eine Zeile stehen – bevorzugt die <b>abgebende</b>, denn ihr
     * Konto und ihr Betrag sind das, was man im Dateinamen erwartet. Alle übrigen Buchungen gehen
     * unverändert durch, die Reihenfolge bleibt.
     */
    private static List<Booking> pickOnePerTransfer(List<Booking> bookings) {
        // Erst je Gruppe die beste Zeile bestimmen, dann in ursprünglicher Reihenfolge ausgeben.
        Map<String, Booking> bestOfGroup = new HashMap<>();
        for (Booking b : bookings) {
            String group = groupOf(b);
            if (group == null) {
                continue;
            }
            Booking have = bestOfGroup.get(group);
            if (have == null || (have.isIncome && !b.isIncome)) {
                bestOfGroup.put(group, b);
            }
        }
        Map<String, Booking> pending = new LinkedHashMap<>(bestOfGroup);
        List<Booking> out = new ArrayList<>(bookings.size());
        for (Booking b : bookings) {
            String group = groupOf(b);
            if (group == null) {
                out.add(b);
            } else if (pending.remove(group) != null) {
                out.add(bestOfGroup.get(group));
            }
        }
        return out;
    }

    /** Die Umbuchungsgruppe einer Zeile, oder {@code null}, wenn sie zu keiner gehört. */
    private static String groupOf(Booking b) {
        if (b == null || !b.isTransfer || b.transferGroup == null || b.transferGroup.isEmpty()) {
            return null;
        }
        return b.transferGroup;
    }

    /** Jahresordner eines Belegs – er richtet sich nach dem Buchungsdatum. */
    public static int yearOf(long createdAt) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(createdAt);
        return c.get(Calendar.YEAR);
    }
}
