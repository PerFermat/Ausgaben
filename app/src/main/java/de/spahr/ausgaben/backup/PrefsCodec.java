package de.spahr.ausgaben.backup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Wandelt den Inhalt einer SharedPreferences-Datei in JSON um und zurück. Der Typ steht mit im Wert
 * ({@code {"t":"b","v":true}}), weil SharedPreferences typtreu geschrieben werden müssen – ein als
 * String zurückgeschriebenes {@code boolean} würde beim Lesen eine ClassCastException auslösen.
 */
public final class PrefsCodec {

    private static final String T = "t";
    private static final String V = "v";

    private PrefsCodec() {
    }

    /** Alle unterstützten Werte als JSON; unbekannte Typen werden ausgelassen. */
    public static String toJson(Map<String, ?> values) throws JSONException {
        JSONObject out = new JSONObject();
        if (values != null) {
            for (Map.Entry<String, ?> e : values.entrySet()) {
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof String) {
                    item.put(T, "s").put(V, v);
                } else if (v instanceof Boolean) {
                    item.put(T, "b").put(V, v);
                } else if (v instanceof Integer) {
                    item.put(T, "i").put(V, v);
                } else if (v instanceof Long) {
                    item.put(T, "l").put(V, v);
                } else if (v instanceof Float) {
                    item.put(T, "f").put(V, ((Float) v).doubleValue());
                } else if (v instanceof Set) {
                    JSONArray arr = new JSONArray();
                    for (Object o : (Set<?>) v) {
                        arr.put(String.valueOf(o));
                    }
                    item.put(T, "set").put(V, arr);
                } else {
                    continue;   // null oder ein Typ, den SharedPreferences gar nicht kennt
                }
                out.put(e.getKey(), item);
            }
        }
        return out.toString();
    }

    /** Umkehrung von {@link #toJson}; unbekannte Typkennungen werden übersprungen. */
    public static LinkedHashMap<String, Object> fromJson(String json) throws JSONException {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        JSONObject src = new JSONObject(json);
        for (java.util.Iterator<String> it = src.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject item = src.optJSONObject(key);
            if (item == null) {
                continue;
            }
            String type = item.optString(T, "");
            switch (type) {
                case "s":
                    out.put(key, item.optString(V, ""));
                    break;
                case "b":
                    out.put(key, item.optBoolean(V, false));
                    break;
                case "i":
                    out.put(key, item.optInt(V, 0));
                    break;
                case "l":
                    out.put(key, item.optLong(V, 0L));
                    break;
                case "f":
                    out.put(key, (float) item.optDouble(V, 0d));
                    break;
                case "set":
                    JSONArray arr = item.optJSONArray(V);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        set.add(arr.optString(i, ""));
                    }
                    out.put(key, set);
                    break;
                default:
                    break;
            }
        }
        return out;
    }
}
