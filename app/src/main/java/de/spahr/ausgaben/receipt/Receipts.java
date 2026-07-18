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

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Dateinamen, die noch aufs Netzlaufwerk hochzuladen sind. */
    public static synchronized Set<String> pending(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_PENDING, new HashSet<>()));
    }

    public static synchronized void addPending(Context ctx, String file) {
        Set<String> s = pending(ctx);
        if (s.add(file)) {
            prefs(ctx).edit().putStringSet(KEY_PENDING, s).apply();
        }
    }

    public static synchronized void removePending(Context ctx, String file) {
        Set<String> s = pending(ctx);
        if (s.remove(file)) {
            prefs(ctx).edit().putStringSet(KEY_PENDING, s).apply();
        }
    }
}
