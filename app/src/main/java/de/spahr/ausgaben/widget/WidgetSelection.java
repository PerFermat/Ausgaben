package de.spahr.ausgaben.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Gewähltes Konto/Ort des Typ-Widgets (per Wechsel-Knopf durchgeschaltet). Ohne Auswahl gilt das
 * Standardkonto/der Standardort. Eine Auswahl {@code place == ""} bedeutet „ganzes Konto" (Kontosaldo).
 */
final class WidgetSelection {

    private static final String PREFS = "widget_selection";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_PLACE = "place";
    private static final String KEY_SET = "set";
    private static final String KEY_TS = "ts";

    /** Nach dieser Zeit springt die Auswahl automatisch auf Standardkonto/-ort zurück. */
    static final long TIMEOUT_MS = 60_000L;

    private WidgetSelection() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** {@code [account, place]}; ohne eigene (noch gültige) Auswahl das Standardkonto + Standardort. */
    static String[] current(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean(KEY_SET, false)
                && System.currentTimeMillis() - p.getLong(KEY_TS, 0) < TIMEOUT_MS) {
            return new String[]{p.getString(KEY_ACCOUNT, ""), p.getString(KEY_PLACE, "")};
        }
        String account = new SettingsStore(ctx).getDefaultAccount();
        String place = account.isEmpty() ? "" : new PlacesStore(ctx).getDefaultPlace(account);
        return new String[]{account == null ? "" : account, place == null ? "" : place};
    }

    /** Setzt die Auswahl auf Standardkonto/-ort zurück (Auto-Rücksprung nach {@link #TIMEOUT_MS}). */
    static void reset(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_SET, false).apply();
    }

    private static void set(Context ctx, String account, String place) {
        prefs(ctx).edit()
                .putString(KEY_ACCOUNT, account == null ? "" : account)
                .putString(KEY_PLACE, place == null ? "" : place)
                .putLong(KEY_TS, System.currentTimeMillis())
                .putBoolean(KEY_SET, true)
                .apply();
    }

    /**
     * Schaltet auf die nächste Auswahl weiter: über alle aktiven Konten und – falls vorhanden – deren Orte
     * (ein Konto ohne Orte erscheint als „ganzes Konto"). Läuft auf einem Hintergrund-Thread.
     */
    static void advance(Context ctx) {
        List<String[]> entries = entries(ctx);
        if (entries.isEmpty()) {
            return;
        }
        String[] cur = current(ctx);
        int idx = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i)[0].equals(cur[0]) && entries.get(i)[1].equals(cur[1])) {
                idx = i;
                break;
            }
        }
        String[] next = entries.get((idx + 1) % entries.size());
        set(ctx, next[0], next[1]);
    }

    /** Alle wählbaren {@code [account, place]}-Einträge (Konto ohne Orte → {@code place == ""}). */
    private static List<String[]> entries(Context ctx) {
        List<String[]> out = new ArrayList<>();
        PlacesStore places = new PlacesStore(ctx);
        List<String> accounts = AppDatabase.getInstance(ctx).accountDao().getActiveNames();
        for (String account : accounts) {
            List<String> ps = places.getPlaces(account);
            if (ps == null || ps.isEmpty()) {
                out.add(new String[]{account, ""});
            } else {
                for (String place : ps) {
                    out.add(new String[]{account, place});
                }
            }
        }
        return out;
    }
}
