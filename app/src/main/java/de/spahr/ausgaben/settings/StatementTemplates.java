package de.spahr.ausgaben.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Was die App über die Abrechnungen der eigenen Banken gelernt hat — die Ankerregeln je Vorlage und die
 * Zuordnung ISIN → Wertpapier, soweit sie nicht schon aus KMyMoney kommt.
 *
 * <p>Liegt wie die Orte ({@link PlacesStore}) bewusst außerhalb der Room-Datenbank: das Gelernte soll
 * „Datenbank zurücksetzen" und einen Import überstehen. In die Sicherung kommt es nicht von selbst —
 * die Datei steht dafür namentlich in {@code BackupStore.PREFS_FILES}.</p>
 *
 * <p>Speicherformat unter {@code templates}: eine Liste von
 * {@code {"a":"buy","r":{"NET":{"t":["Endbetrag zu Ihren Lasten"],"d":"SAME_LINE","s":false,"c":"EUR"}, …}}}.
 * Unter {@code isins} steht je ISIN Depot, Wertpapier-ID und Name.</p>
 */
public class StatementTemplates {

    private static final String PREFS = "ausgaben_statements";
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_ISINS = "isins";
    /**
     * Trennt Depot, Wertpapier-ID und Name im gespeicherten Wert. Das Steuerzeichen
     * "unit separator" kann in keinem der drei vorkommen; als Fluchtfolge geschrieben, weil es
     * sonst unsichtbar im Quelltext stuende.
     */
    private static final String SEP = "\u001F";

    private final SharedPreferences prefs;

    public StatementTemplates(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- Vorlagen ----

    /**
     * Die Vorlage, die zu diesem Dokument passt, oder {@code null}. Passen mehrere, gewinnt die mit den
     * meisten Treffern (siehe {@link StatementTemplate#score}); bei Gleichstand die zuletzt gelernte —
     * sie ist die genauere, denn beim Lernen wird eine gleichartige Vorlage ersetzt.
     */
    public StatementTemplate match(PdfText text) {
        StatementTemplate best = null;
        int bestScore = 0;
        for (StatementTemplate t : all()) {
            int score = t.score(text);
            if (score > 0 && score >= bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    public List<StatementTemplate> all() {
        List<StatementTemplate> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_TEMPLATES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                StatementTemplate t = fromJson(arr.optJSONObject(i));
                if (t != null && !t.isEmpty()) {
                    out.add(t);
                }
            }
        } catch (Exception ignored) {
            // Unlesbarer Bestand: lieber ohne Vorlagen weiterarbeiten als die App daran scheitern lassen.
        }
        return out;
    }

    /**
     * Merkt sich eine Vorlage. Eine bereits vorhandene mit derselben Aktion <b>und</b> demselben
     * Hauptanker wird ersetzt — sonst sammelten sich mit jedem Lernvorgang Dubletten an, von denen die
     * ältere zufällig gewinnen könnte.
     */
    public void save(StatementTemplate template) {
        if (template == null || template.isEmpty()) {
            return;
        }
        List<StatementTemplate> kept = new ArrayList<>();
        for (StatementTemplate t : all()) {
            if (!sameKind(t, template)) {
                kept.add(t);
            }
        }
        kept.add(template);
        JSONArray arr = new JSONArray();
        for (StatementTemplate t : kept) {
            JSONObject o = toJson(t);
            if (o != null) {
                arr.put(o);
            }
        }
        prefs.edit().putString(KEY_TEMPLATES, arr.toString()).apply();
    }

    /**
     * Schreibt die ganze Liste — für die Regelseite, auf der von Hand bearbeitet wird.
     *
     * <p>Nicht {@link #save} nehmen: das verdrängt eine gleichartige Vorlage über Aktion und
     * NET-Beschriftung. Wird ausgerechnet die bearbeitet, fände es die bisherige nicht wieder und legte
     * eine Dublette an.</p>
     */
    public void saveAll(List<StatementTemplate> templates) {
        JSONArray arr = new JSONArray();
        for (StatementTemplate t : templates) {
            if (t == null || t.isEmpty()) {
                continue;
            }
            JSONObject o = toJson(t);
            if (o != null) {
                arr.put(o);
            }
        }
        prefs.edit().putString(KEY_TEMPLATES, arr.toString()).apply();
    }

    /** Alles Gelernte verwerfen (Auslieferungszustand). */
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    private static boolean sameKind(StatementTemplate a, StatementTemplate b) {
        if (a.action == null ? b.action != null : !a.action.equals(b.action)) {
            return false;
        }
        AnchorRule ra = a.rule(StatementTemplate.Field.NET);
        AnchorRule rb = b.rule(StatementTemplate.Field.NET);
        if (ra == null || rb == null || ra.anchors.isEmpty() || rb.anchors.isEmpty()) {
            return false;
        }
        return ra.anchors.get(0).equalsIgnoreCase(rb.anchors.get(0));
    }

    // ---- ISIN → Wertpapier ----

    /**
     * Das Wertpapier zu einer ISIN als {@code {Depot, kmyId, Name}}, oder {@code null}. Nur für ISINs,
     * die in KMyMoney nicht gepflegt sind — dort gefundene haben Vorrang und brauchen dieses Gedächtnis
     * nicht. Der Name steht mit dabei, damit die Maske ohne zweite Abfrage öffnen kann.
     */
    public String[] security(String isin) {
        if (isin == null || isin.trim().isEmpty()) {
            return null;
        }
        try {
            String value = new JSONObject(prefs.getString(KEY_ISINS, "{}"))
                    .optString(isin.trim().toUpperCase(java.util.Locale.ROOT), "");
            String[] parts = value.split(SEP, -1);
            return parts.length == 3 ? parts : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void rememberSecurity(String isin, String depot, String kmyId, String name) {
        if (isin == null || isin.trim().isEmpty() || depot == null || kmyId == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(prefs.getString(KEY_ISINS, "{}"));
            root.put(isin.trim().toUpperCase(java.util.Locale.ROOT),
                    depot + SEP + kmyId + SEP + (name == null ? "" : name));
            prefs.edit().putString(KEY_ISINS, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---- JSON ----

    private static JSONObject toJson(StatementTemplate t) {
        try {
            JSONObject o = new JSONObject();
            o.put("a", t.action == null ? "" : t.action);
            JSONObject rules = new JSONObject();
            for (Map.Entry<StatementTemplate.Field, AnchorRule> e : t.rules().entrySet()) {
                AnchorRule r = e.getValue();
                JSONObject ro = new JSONObject();
                ro.put("t", new JSONArray(r.anchors));
                ro.put("d", r.direction.name());
                ro.put("s", r.sum);
                ro.put("c", r.currency);
                rules.put(e.getKey().name(), ro);
            }
            o.put("r", rules);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    private static StatementTemplate fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        Map<StatementTemplate.Field, AnchorRule> rules = new EnumMap<>(StatementTemplate.Field.class);
        JSONObject r = o.optJSONObject("r");
        if (r != null) {
            for (StatementTemplate.Field field : StatementTemplate.Field.values()) {
                JSONObject ro = r.optJSONObject(field.name());
                if (ro == null) {
                    continue;
                }
                List<String> anchors = new ArrayList<>();
                JSONArray arr = ro.optJSONArray("t");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        anchors.add(arr.optString(i, ""));
                    }
                }
                if (anchors.isEmpty()) {
                    continue;
                }
                AnchorRule.Direction dir;
                try {
                    dir = AnchorRule.Direction.valueOf(
                            ro.optString("d", AnchorRule.Direction.SAME_LINE.name()));
                } catch (IllegalArgumentException e) {
                    dir = AnchorRule.Direction.SAME_LINE;
                }
                rules.put(field, new AnchorRule(anchors, dir, ro.optBoolean("s", false),
                        ro.optString("c", "")));
            }
        }
        return new StatementTemplate(o.optString("a", ""), rules);
    }
}
