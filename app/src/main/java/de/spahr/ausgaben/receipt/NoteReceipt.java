package de.spahr.ausgaben.receipt;

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

    /** Neuer, eindeutiger Dateiname der ersten Seite: {@code <uuid>_Seite1.jpg}. */
    public static String newFileName() {
        return pageName(newBase(), 1);
    }

    /**
     * Neue, für die Buchung feste Basis – eine nackte UUID. Alle Seiten einer Buchung teilen sie sich;
     * unterschieden werden sie nur durch den Zusatz {@code _Seite<n>}. Das Jahr steckt <b>nicht</b> im
     * Namen, sondern im Jahresordner auf dem Server (es stammt aus dem Buchungsdatum).
     */
    public static String newBase() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Was für eine Belegseite in die Notiz geschrieben wird. Für neue Belege ist das die nackte UUID; ein
     * <b>Altbeleg</b> mit Jahres-Präfix behält seinen vollen Dateinamen, damit sich bestehende Notizen –
     * und damit die KMyMoney-Memos – nicht ändern.
     */
    public static String tagOf(String pageName) {
        return yearOf(pageName) >= 0 ? pageName : baseOf(pageName);
    }

    /** Dateiname der {@code page}-ten Seite zu einer Basis: {@code <basis>_Seite<n>.jpg}. */
    public static String pageName(String base, int page) {
        return base + PAGE + page + ".jpg";
    }

    /**
     * Die gemeinsame Basis eines Belegnamens: Endung, ein evtl. {@code _original} und ein evtl.
     * {@code _Seite<n>} werden abgeschnitten. Deckt alle drei Namensgenerationen ab – {@code abc}
     * (nackte UUID aus der Notiz), {@code abc_Seite2.jpg} (neu) sowie {@code 2026_abc.jpg} und
     * {@code 2026_abc_Seite2_original.jpg} (Altbestand mit Jahres-Präfix, das erhalten bleibt).
     */
    public static String baseOf(String file) {
        if (file == null) {
            return null;
        }
        String s = stem(file);
        int cut = pageSuffixAt(s);
        return cut < 0 ? s : s.substring(0, cut);
    }

    /** Seitennummer eines Belegnamens; ohne Seitenzusatz (alte Schreibweise) ist es die <b>1</b>. */
    public static int pageOf(String file) {
        if (file == null) {
            return 1;
        }
        String s = stem(file);
        int cut = pageSuffixAt(s);
        if (cut < 0) {
            return 1;
        }
        for (String suffix : PAGE_SUFFIXES) {
            if (s.startsWith(suffix, cut)) {
                return Integer.parseInt(s.substring(cut + suffix.length()));
            }
        }
        return 1;
    }

    /** Trägt der Name überhaupt einen Seitenzusatz? Altbelege der ersten Stunde tun das nicht. */
    public static boolean hasPageSuffix(String file) {
        return file != null && pageSuffixAt(stem(file)) >= 0;
    }

    /** Dateiname ohne Endung und ohne {@code _original}. */
    private static String stem(String file) {
        String s = file.trim();
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(0, dot);
        }
        return s.endsWith(ORIGINAL) ? s.substring(0, s.length() - ORIGINAL.length()) : s;
    }

    /**
     * Wo der Seitenzusatz beginnt, sonst {@code -1}. Erkennt das aktuelle {@code _p<n>} ebenso wie das
     * frühere deutsche {@code _Seite<n>}, damit bereits so benannte Dateien weiter gefunden werden.
     */
    private static int pageSuffixAt(String stem) {
        for (String suffix : PAGE_SUFFIXES) {
            int p = stem.lastIndexOf(suffix);
            if (p > 0 && isNumber(stem.substring(p + suffix.length()))) {
                return p;
            }
        }
        return -1;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 9) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Namenszusatz des unbearbeiteten Originals. */
    private static final String ORIGINAL = "_original";
    /**
     * Namenszusatz der Seitennummer (die Nummer folgt unmittelbar). Geschrieben wird das sprachneutrale
     * {@code _p}; {@code _Seite} stammt aus einer früheren Fassung und wird nur noch gelesen.
     */
    private static final String PAGE = "_p";
    private static final String[] PAGE_SUFFIXES = {PAGE, "_Seite"};

    /**
     * Name des unbearbeiteten Originals zu einem Beleg: {@code 2026_abc.jpg} wird zu
     * {@code 2026_abc_original.jpg}. Da nur hinten angehängt wird, bleibt das Jahres-Präfix – und damit der
     * Jahresordner beim Hochladen – erhalten. Ein bereits so benannter Name wird unverändert zurückgegeben.
     */
    public static String originalName(String file) {
        if (file == null || file.trim().isEmpty() || isOriginal(file)) {
            return file;
        }
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file + ORIGINAL : file.substring(0, dot) + ORIGINAL + file.substring(dot);
    }

    /** Ist {@code file} der Name eines Originals? */
    public static boolean isOriginal(String file) {
        if (file == null) {
            return false;
        }
        int dot = file.lastIndexOf('.');
        String base = dot < 0 ? file : file.substring(0, dot);
        return base.endsWith(ORIGINAL);
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
