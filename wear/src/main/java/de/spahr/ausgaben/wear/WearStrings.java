package de.spahr.ausgaben.wear;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hält die vom Phone übertragenen Wear-Texte der aktiven Sprache (Schlüssel {@code wear_*} → Text) und
 * persistiert sie (SharedPreferences). Fehlt ein Schlüssel, greift die gebündelte DE/EN-Ressource. Wird
 * von der {@code Resources}-Überschreibung {@link WearLocaleWrapper} gelesen.
 */
public final class WearStrings {

    private static final String PREFS = "wear_language";
    private static final String KEY_CODE = "code";
    private static final String KEY_STRINGS = "strings";

    private static volatile Map<String, String> map = Collections.emptyMap();
    private static volatile Locale locale = Locale.GERMAN;
    private static volatile String code = "de";
    private static volatile boolean loaded;

    private WearStrings() {
    }

    /** Einmalig aus dem Speicher laden (billig, SharedPreferences). */
    public static void ensureLoaded(Context context) {
        if (loaded) {
            return;
        }
        SharedPreferences p = prefs(context);
        apply(p.getString(KEY_CODE, "de"), p.getString(KEY_STRINGS, "{}"));
        loaded = true;
    }

    /** Vom Phone empfangene Sprache übernehmen und speichern. */
    public static void update(Context context, String code, String stringsJson) {
        prefs(context).edit().putString(KEY_CODE, code).putString(KEY_STRINGS, stringsJson).apply();
        apply(code, stringsJson);
        loaded = true;
    }

    private static void apply(String code, String stringsJson) {
        Map<String, String> m = new HashMap<>();
        try {
            JSONObject o = new JSONObject(stringsJson);
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {
        }
        map = m;
        WearStrings.code = code == null || code.isEmpty() ? "de" : code;
        locale = new Locale(WearStrings.code);
    }

    public static String get(String key) {
        return map.get(key);
    }

    public static Locale locale() {
        return locale;
    }

    /** Aktiver Sprachcode (aus dem gespeicherten/empfangenen Zustand). */
    public static String code(Context context) {
        ensureLoaded(context);
        return code;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
