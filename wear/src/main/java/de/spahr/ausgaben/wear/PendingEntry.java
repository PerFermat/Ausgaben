package de.spahr.ausgaben.wear;

import org.json.JSONException;
import org.json.JSONObject;

/** Eine noch nicht (bestätigt) übertragene Sprach-Ausgabe auf der Uhr. */
public class PendingEntry {

    public final String id;
    public final String text;
    public final long timestamp;

    public PendingEntry(String id, String text, long timestamp) {
        this.id = id;
        this.text = text;
        this.timestamp = timestamp;
    }

    /** JSON für die Data-Layer-Übertragung: {"id":…, "text":…, "timestamp":…}. */
    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("text", text);
        o.put("timestamp", timestamp);
        return o.toString();
    }

    static PendingEntry fromJson(JSONObject o) {
        return new PendingEntry(o.optString("id", ""), o.optString("text", ""),
                o.optLong("timestamp", 0));
    }
}
