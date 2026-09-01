package de.spahr.ausgaben.i18n;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.TranslationDao;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * JSON-Struktur für den Export der Sprach-Vorlage und den Import einer manuell befüllten Sprachdatei.
 * Format: {@code {"language":"","displayName":"","defaultCurrency":"","numberFormat":"",
 * "strings":{ key:{"de":…,"en":…,"value":""}, … }}}. {@code defaultCurrency}/{@code numberFormat} sind
 * optional (ältere Sprachdateien ohne diese Felder importieren weiterhin klaglos, mit Fallback) – daraus
 * leitet das Onboarding beim Anlegen eines neuen Profils Währung und Zahlenformat für diese Sprache ab.
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
        root.put("defaultCurrency", "");
        root.put("numberFormat", "");
        root.put("strings", strings);
        return root.toString(2);
    }

    /**
     * Ergebnis des Imports: Sprachcode, Anzeigename, Standard-Währung/-Zahlenformat für diese Sprache
     * und die befüllten Übersetzungswerte (leere → englischer Fallback).
     */
    public static final class Parsed {
        public final String code;
        public final String name;
        public final String defaultCurrency;
        public final String numberFormat;
        public final Map<String, String> values;

        Parsed(String code, String name, String defaultCurrency, String numberFormat,
               Map<String, String> values) {
            this.code = code;
            this.name = name;
            this.defaultCurrency = defaultCurrency;
            this.numberFormat = numberFormat;
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
        String defaultCurrency = root.optString("defaultCurrency", "").trim();
        if (defaultCurrency.isEmpty()) {
            defaultCurrency = "€";
        }
        String numberFormat = root.optString("numberFormat", "").trim();
        if (numberFormat.isEmpty()) {
            numberFormat = SettingsStore.NUMBER_FORMAT_PLAIN_COMMA;
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
        return new Parsed(code, name, defaultCurrency, numberFormat, values);
    }
}
