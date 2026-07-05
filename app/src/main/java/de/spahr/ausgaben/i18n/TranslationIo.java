package de.spahr.ausgaben.i18n;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.TranslationDao;

/**
 * JSON-Struktur für den Export der Sprach-Vorlage und den Import einer manuell befüllten Sprachdatei.
 * Format: {@code {"language":"","displayName":"","strings":{ key:{"de":…,"en":…,"value":""}, … }}}.
 */
public final class TranslationIo {

    private TranslationIo() {
    }

    /** Baut die Export-Vorlage: alle Schlüssel mit deutschem + englischem Referenztext und leerem „value". */
    public static String buildTemplate(List<TranslationDao.KeyValue> de,
                                       List<TranslationDao.KeyValue> en) throws JSONException {
        Map<String, String> enMap = new LinkedHashMap<>();
        for (TranslationDao.KeyValue kv : en) {
            enMap.put(kv.key, kv.value);
        }
        JSONObject strings = new JSONObject();
        for (TranslationDao.KeyValue kv : de) {
            JSONObject entry = new JSONObject();
            entry.put("de", kv.value);
            entry.put("en", enMap.containsKey(kv.key) ? enMap.get(kv.key) : "");
            entry.put("value", "");
            strings.put(kv.key, entry);
        }
        JSONObject root = new JSONObject();
        root.put("language", "");
        root.put("displayName", "");
        root.put("strings", strings);
        return root.toString(2);
    }

    /** Ergebnis des Imports: Sprachcode, Anzeigename und die befüllten Werte (leere → englischer Fallback). */
    public static final class Parsed {
        public final String code;
        public final String name;
        public final Map<String, String> values;

        Parsed(String code, String name, Map<String, String> values) {
            this.code = code;
            this.name = name;
            this.values = values;
        }
    }

    public static Parsed parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String code = root.optString("language", "").trim();
        String name = root.optString("displayName", "").trim();
        if (code.isEmpty()) {
            throw new JSONException("Feld 'language' fehlt");
        }
        if (name.isEmpty()) {
            name = code;
        }
        Map<String, String> values = new LinkedHashMap<>();
        JSONObject strings = root.optJSONObject("strings");
        if (strings != null) {
            for (java.util.Iterator<String> it = strings.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject entry = strings.optJSONObject(key);
                if (entry == null) {
                    continue;
                }
                String value = entry.optString("value", "").trim();
                if (value.isEmpty()) {
                    value = entry.optString("en", "").trim(); // Fallback: englischer Referenztext
                }
                if (!value.isEmpty()) {
                    values.put(key, value);
                }
            }
        }
        return new Parsed(code, name, values);
    }
}
