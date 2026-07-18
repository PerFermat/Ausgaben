package de.spahr.ausgaben.receipt;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verwaltet den Beleg-Verweis im Notizfeld einer Buchung – analog zum {@code GPS:}-Tag. Der Dateiname eines
 * Belegfotos wird als {@code BELEG: <jahr>_<uuid>.jpg} an die Notiz angehängt; da die Notiz als KMyMoney-Memo
 * exportiert und re-importiert wird, überlebt die Zuordnung einen Neu-Import (kein Fingerprint, keine Tabelle).
 *
 * <p>Rein und testbar – kein Android. Der {@code BELEG:}-Tag und ein evtl. vorhandener {@code GPS:}-Tag werden
 * unabhängig voneinander behandelt (kein gegenseitiges Überschreiben).</p>
 */
public final class NoteReceipt {

    /** Passt auf „BELEG: <datei>" (Datei = zusammenhängende Nicht-Leerzeichen). */
    private static final Pattern TAG = Pattern.compile("\\s*BELEG:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    private NoteReceipt() {
    }

    /** Dateiname aus dem {@code BELEG:}-Tag der Notiz, sonst {@code null}. */
    public static String fileName(String note) {
        if (note == null) {
            return null;
        }
        Matcher m = TAG.matcher(note);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    /** Setzt/ersetzt den {@code BELEG:}-Tag und hängt ihn ans Ende an (GPS-Tag bleibt unberührt). */
    public static String withFileName(String note, String file) {
        String base = strip(note);
        if (file == null || file.trim().isEmpty()) {
            return base;
        }
        String tag = "BELEG: " + file.trim();
        return base.isEmpty() ? tag : base + " " + tag;
    }

    /** Entfernt einen evtl. vorhandenen {@code BELEG:}-Tag; der Rest (inkl. GPS) bleibt erhalten. */
    public static String strip(String note) {
        if (note == null) {
            return "";
        }
        return TAG.matcher(note).replaceAll("").replaceAll("\\s+$", "").replaceAll("^\\s+", "");
    }

    /** Neuer, eindeutiger Dateiname {@code <jahr>_<uuid>.jpg}. */
    public static String newFileName(int year) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format(Locale.US, "%04d_%s.jpg", year, uuid);
    }

    /** Jahr aus dem Dateinamen-Präfix ({@code <jahr>_…}); {@code -1}, wenn nicht ableitbar. */
    public static int yearOf(String file) {
        if (file == null) {
            return -1;
        }
        int us = file.indexOf('_');
        if (us < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(file.substring(0, us));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
