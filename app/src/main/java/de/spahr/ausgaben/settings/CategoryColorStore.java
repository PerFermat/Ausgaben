package de.spahr.ausgaben.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Feste Farbzuordnung für die Kategorien der Seite „Wofür geht mein Geld?". Jede Kategorie hat – unabhängig
 * von ihrem Listenplatz – <b>immer dieselbe</b> Farbe: entweder eine manuell gewählte oder einen
 * deterministischen Standard aus der {@link #PALETTE}. Die manuellen Zuordnungen liegen als JSON-Objekt
 * {@code {Kategorie: Farbwert}} in den normalen App-Prefs (kein DB-/Migrationsbedarf).
 */
public class CategoryColorStore {

    /** Kontrastreiche Palette (in Hell und Dunkel lesbar) – zugleich Auswahl im Farb-Editor. */
    public static final int[] PALETTE = {
            0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726, 0xFFEF5350, 0xFFAB47BC,
            0xFF26C6DA, 0xFFFFCA28, 0xFF8D6E63, 0xFF5C6BC0, 0xFFEC407A,
            0xFF9CCC65, 0xFF29B6F6, 0xFFFF7043, 0xFF78909C, 0xFF7E57C2};

    private static final String PREFS = "ausgaben_settings";
    private static final String KEY_COLORS = "category_colors";

    private final SharedPreferences prefs;

    public CategoryColorStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Wirk-Farbe einer Kategorie: manuell gewählt, sonst deterministischer Standard aus der Palette. */
    public int colorFor(String category) {
        Integer manual = manualColor(category);
        return manual != null ? manual : defaultColor(category);
    }

    /** Standardfarbe (ohne manuelle Wahl) – stabil über den Kategorienamen, damit sie sich nie verschiebt. */
    public static int defaultColor(String category) {
        String key = category == null ? "" : category;
        return PALETTE[Math.floorMod(key.hashCode(), PALETTE.length)];
    }

    /** Manuell gesetzte Farbe oder {@code null}, wenn keine hinterlegt ist. */
    public Integer manualColor(String category) {
        if (category == null || category.isEmpty()) {
            return null;
        }
        JSONObject obj = load();
        if (!obj.has(category)) {
            return null;
        }
        try {
            return (int) (obj.getLong(category));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Setzt die Farbe einer Kategorie fest. */
    public void setColor(String category, int color) {
        if (category == null || category.isEmpty()) {
            return;
        }
        JSONObject obj = load();
        try {
            // Als long ablegen, damit das negative ARGB-Vorzeichen (0xFF…) erhalten bleibt.
            obj.put(category, color & 0xFFFFFFFFL);
        } catch (JSONException ignored) {
            return;
        }
        prefs.edit().putString(KEY_COLORS, obj.toString()).apply();
    }

    /** Entfernt die manuelle Farbe – die Kategorie fällt auf ihren Standard zurück. */
    public void clear(String category) {
        JSONObject obj = load();
        if (obj.has(category)) {
            obj.remove(category);
            prefs.edit().putString(KEY_COLORS, obj.toString()).apply();
        }
    }

    private JSONObject load() {
        String raw = prefs.getString(KEY_COLORS, "");
        if (raw == null || raw.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

}
