package de.spahr.ausgaben.i18n;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Language;
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

    /** Die gebaute Vorlage samt Auskunft, wie viele ihrer Texte leer geblieben sind. */
    public static final class Template {
        public final String json;
        public final int missing;
        public final int total;

        Template(String json, int missing, int total) {
            this.json = json;
            this.missing = missing;
            this.total = total;
        }
    }

    /**
     * Baut die Export-Vorlage: alle Schlüssel mit deutschem und englischem Referenztext.
     *
     * <p>Ist {@code current} gesetzt (eine hochgeladene Sprache), steht deren Text schon im „value" und
     * der Kopf ist aus {@code language} vorbelegt – dann muß für einen Nachtrag nur noch das gefüllt
     * werden, was die App seit dem letzten Mal dazubekommen hat. Ohne {@code current} (eingebaute
     * Sprache) bleibt alles leer: dort ist die Vorlage der Anfang einer <em>neuen</em> Sprache.</p>
     */
    public static Template buildTemplate(List<TranslationDao.KeyValue> de,
                                         List<TranslationDao.KeyValue> en,
                                         List<TranslationDao.KeyValue> current,
                                         Language language) throws JSONException {
        Map<String, String> enMap = new LinkedHashMap<>();
        for (TranslationDao.KeyValue kv : en) {
            enMap.put(kv.key, kv.value);
        }
        Map<String, String> vorhanden = new LinkedHashMap<>();
        if (current != null) {
            for (TranslationDao.KeyValue kv : current) {
                if (kv.value != null && !kv.value.trim().isEmpty()) {
                    vorhanden.put(kv.key, kv.value);
                }
            }
        }
        int missing = 0;
        JSONObject strings = new JSONObject();
        for (TranslationDao.KeyValue kv : de) {
            String value = vorhanden.containsKey(kv.key) ? vorhanden.get(kv.key) : "";
            if (value.isEmpty()) {
                missing++;
            }
            JSONObject entry = new JSONObject();
            entry.put("de", kv.value);
            entry.put("en", enMap.containsKey(kv.key) ? enMap.get(kv.key) : "");
            entry.put("value", value);
            strings.put(kv.key, entry);
        }
        JSONObject root = new JSONObject();
        root.put("language", language == null ? "" : language.code);
        root.put("displayName", language == null ? "" : language.name);
        root.put("defaultCurrency", language == null ? "" : language.defaultCurrency);
        root.put("numberFormat", language == null ? "" : language.numberFormat);
        root.put("strings", strings);
        return new Template(root.toString(2), missing, de.size());
    }

    /**
     * Ergebnis des Imports: Sprachcode, Anzeigename, Standard-Währung/-Zahlenformat für diese Sprache
     * und die befüllten Übersetzungswerte. Leer gelassene Schlüssel fehlen hier – sie sollen fehlen
     * bleiben (siehe {@link #parse(String)}).
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

    /**
     * Liest eine befüllte Sprachdatei. Ein leer gelassener Text wird <b>nicht</b> übernommen: angezeigt
     * wird dort ohnehin Englisch, weil {@link LocaleManager} es unter jede Sprache legt. Würde hier
     * ersatzweise der englische Text gespeichert, gälte der Schlüssel als übersetzt – die nächste
     * Vorlage brächte ihn englisch vorbelegt zurück und die Fehlliste wäre für immer leer.
     */
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
                if (!value.isEmpty()) {
                    values.put(key, value);
                }
            }
        }
        return new Parsed(code, name, defaultCurrency, numberFormat, values);
    }
}
