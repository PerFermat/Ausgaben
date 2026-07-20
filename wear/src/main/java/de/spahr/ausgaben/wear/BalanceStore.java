package de.spahr.ausgaben.wear;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält die vom Phone übertragenen, fertig formatierten Konto/Ort-Salden (Liste) sowie die per Wechsel-Knopf
 * gewählte Position. Jede Listenzeile ist {@code account + SEP + Anzeige} (Anzeige z. B.
 * „Geldbeutel: 70,00 €"); Position 0 = Standardkonto/-ort. Ohne Liste gilt der einzelne {@code text}. Die
 * Auswahl springt nach {@link #TIMEOUT_MS} automatisch auf Position 0 zurück.
 */
public final class BalanceStore {

    private static final String PREFS = "wear_balance";
    private static final String KEY_TEXT = "text";
    private static final String KEY_LIST = "list";
    private static final String KEY_INDEX = "index";
    private static final String KEY_TS = "ts";
    /** Trennzeichen zwischen Konto und Anzeige innerhalb einer Zeile (Unit Separator). */
    private static final char SEP = '\u001F';

    /** Nach dieser Zeit springt die Auswahl automatisch auf Standardkonto/-ort (Position 0) zurück. */
    public static final long TIMEOUT_MS = 60_000L;

    private BalanceStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Standard-Anzeige (Position 0) speichern (Rückwärtskompatibilität). */
    public static void save(Context context, String text) {
        prefs(context).edit().putString(KEY_TEXT, text == null ? "" : text).apply();
    }

    /** Liste aller Konto/Ort-Salden speichern (Zeilen per {@code \n}). */
    public static void saveList(Context context, String list) {
        prefs(context).edit().putString(KEY_LIST, list == null ? "" : list).apply();
    }

    /** Zeilen der Liste ({@code account + SEP + Anzeige}); leer, wenn keine Liste vorliegt. */
    private static List<String> entries(Context context) {
        List<String> out = new ArrayList<>();
        String raw = prefs(context).getString(KEY_LIST, "");
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    public static int count(Context context) {
        return entries(context).size();
    }

    /** Gewählter Index (0-basiert); nach Timeout oder außerhalb der Liste wieder 0. */
    public static int selectedIndex(Context context) {
        SharedPreferences p = prefs(context);
        if (System.currentTimeMillis() - p.getLong(KEY_TS, 0) >= TIMEOUT_MS) {
            return 0;
        }
        int idx = p.getInt(KEY_INDEX, 0);
        int n = count(context);
        return idx < n ? idx : 0;
    }

    /**
     * {@code true}, wenn seit dem letzten {@link #advance()} weniger als {@link #TIMEOUT_MS} vergangen
     * sind – unabhängig davon, ob die Auswahl dabei bei Position 0 (Standard) gelandet ist. Grundlage für
     * die Anzeige „gerade gewähltes Konto zeigen" statt Saldo/Übertragungs-Hinweis.
     */
    public static boolean isRecentlySelected(Context context) {
        SharedPreferences p = prefs(context);
        long ts = p.getLong(KEY_TS, 0);
        return ts > 0 && System.currentTimeMillis() - ts < TIMEOUT_MS;
    }

    /** Schaltet auf die nächste Konto/Ort-Position weiter (Zeitstempel für den Auto-Rücksprung). */
    public static void advance(Context context) {
        int n = count(context);
        if (n < 2) {
            return;
        }
        int next = (selectedIndex(context) + 1) % n;
        prefs(context).edit().putInt(KEY_INDEX, next)
                .putLong(KEY_TS, System.currentTimeMillis()).apply();
    }

    /** Zurück auf Standardkonto/-ort (Position 0). */
    public static void reset(Context context) {
        prefs(context).edit().putInt(KEY_INDEX, 0).apply();
    }

    /** Verbleibende Zeit (ms) bis zum Auto-Rücksprung; 0, wenn bereits Standard/abgelaufen (für Tile-Freshness). */
    public static long remainingMs(Context context) {
        SharedPreferences p = prefs(context);
        long ts = p.getLong(KEY_TS, 0);
        if (ts <= 0) {
            return 0;
        }
        long rem = TIMEOUT_MS - (System.currentTimeMillis() - ts);
        return rem > 0 ? rem : 0;
    }

    /** Anzeige der aktuell gewählten Position (z. B. „Geldbeutel: 70,00 €"); leer → keiner. */
    public static String get(Context context) {
        List<String> e = entries(context);
        if (!e.isEmpty()) {
            return display(e.get(selectedIndex(context)));
        }
        return prefs(context).getString(KEY_TEXT, "");
    }

    /** Konto der aktuell gewählten Position (leer = Standardkonto des Phones). */
    public static String selectedAccount(Context context) {
        List<String> e = entries(context);
        return e.isEmpty() ? "" : parts(e.get(selectedIndex(context)))[0];
    }

    /** Ort der aktuell gewählten Position (leer = Standardort/ganzes Konto). */
    public static String selectedPlace(Context context) {
        List<String> e = entries(context);
        return e.isEmpty() ? "" : parts(e.get(selectedIndex(context)))[1];
    }

    private static String display(String line) {
        return parts(line)[2];
    }

    /** Zerlegt eine Zeile in {@code [account, place, display]}. */
    private static String[] parts(String line) {
        int a = line.indexOf(SEP);
        if (a < 0) {
            return new String[]{"", "", line};
        }
        int b = line.indexOf(SEP, a + 1);
        if (b < 0) {
            return new String[]{line.substring(0, a), "", line.substring(a + 1)};
        }
        return new String[]{line.substring(0, a), line.substring(a + 1, b), line.substring(b + 1)};
    }
}
