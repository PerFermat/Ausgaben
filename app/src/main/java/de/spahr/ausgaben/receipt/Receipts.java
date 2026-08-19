package de.spahr.ausgaben.receipt;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Lokale Ablage der Belegfotos (app-privates {@code belege/}-Verzeichnis) und die Merkliste der noch nicht
 * hochgeladenen Dateien (SharedPreferences – keine Room-Tabelle, keine Migration).
 */
public final class Receipts {

    private static final String PREFS = "receipts";
    private static final String KEY_PENDING = "pending";

    private Receipts() {
    }

    /** App-privater Ordner der lokal gespeicherten Belege (wird bei Bedarf angelegt). */
    public static File dir(Context ctx) {
        File d = ctx.getExternalFilesDir("belege");
        if (d == null) {
            d = new File(ctx.getFilesDir(), "belege");
        }
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File localFile(Context ctx, String file) {
        return new File(dir(ctx), file);
    }

    /** Auslieferungszustand: alle lokalen Belegdateien und die Merkliste offener Belege entfernen. */
    public static synchronized void reset(Context ctx) {
        File d = dir(ctx);
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        prefs(ctx).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Offene Einträge in der Form {@code <jahr>|<datei>}. Seit die Dateinamen kein Jahr mehr tragen, muss
     * der Jahresordner mitgeführt werden; Einträge aus älteren Versionen stehen ohne {@code |} darin und
     * werden weiter gelesen (Jahr dann aus dem Dateinamen).
     */
    public static synchronized Set<String> pending(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_PENDING, new HashSet<>()));
    }

    /** Dateiname eines Merklisten-Eintrags. */
    public static String entryFile(String entry) {
        int bar = entry == null ? -1 : entry.indexOf('|');
        return bar < 0 ? entry : entry.substring(bar + 1);
    }

    /** Jahresordner eines Merklisten-Eintrags; {@code -1}, wenn er sich nicht ermitteln lässt. */
    public static int entryYear(String entry) {
        int bar = entry == null ? -1 : entry.indexOf('|');
        if (bar > 0) {
            try {
                return Integer.parseInt(entry.substring(0, bar));
            } catch (NumberFormatException ignored) {
                // fällt unten auf den Dateinamen zurück
            }
        }
        return NoteReceipt.yearOf(entryFile(entry));
    }

    /** Merkt eine Datei zum Hochladen vor; das Jahr bestimmt den Zielordner auf dem Server. */
    public static synchronized void addPending(Context ctx, String file, int year) {
        Set<String> s = pending(ctx);
        removeFile(s, file);
        if (s.add(year + "|" + file)) {
            prefs(ctx).edit().putStringSet(KEY_PENDING, s).apply();
        }
    }

    public static synchronized void removePending(Context ctx, String file) {
        Set<String> s = pending(ctx);
        if (removeFile(s, file)) {
            prefs(ctx).edit().putStringSet(KEY_PENDING, s).apply();
        }
    }

    /** Entfernt alle Einträge zu {@code file} – mit und ohne Jahresangabe. */
    private static boolean removeFile(Set<String> entries, String file) {
        boolean changed = false;
        for (java.util.Iterator<String> it = entries.iterator(); it.hasNext(); ) {
            if (entryFile(it.next()).equals(file)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }
}
