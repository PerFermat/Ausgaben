package de.spahr.ausgaben.wear;

import org.json.JSONException;
import org.json.JSONObject;

/** Eine noch nicht (bestätigt) übertragene Sprach-Buchung auf der Uhr. */
public class PendingEntry {

    public final String id;
    public final String text;
    /** Buchungstyp: {@link WearPaths#TYPE_INCOME}/{@code TYPE_EXPENSE}/{@code TYPE_TRANSFER}. */
    public final String type;
    public final long timestamp;
    /** Frühester Sendezeitpunkt (lokal): erlaubt das 10-Sekunden-Abbrechen vor der Übertragung. */
    public final long readyAt;

    public PendingEntry(String id, String text, String type, long timestamp, long readyAt) {
        this.id = id;
        this.text = text;
        this.type = type;
        this.timestamp = timestamp;
        this.readyAt = readyAt;
    }

    /** JSON für Speicherung + Übertragung: {"id","text","type","timestamp","readyAt"}. */
    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("text", text);
        o.put("type", type);
        o.put("timestamp", timestamp);
        o.put("readyAt", readyAt);
        return o.toString();
    }

    static PendingEntry fromJson(JSONObject o) {
        long ts = o.optLong("timestamp", 0);
        return new PendingEntry(
                o.optString("id", ""),
                o.optString("text", ""),
                o.optString("type", WearPaths.TYPE_EXPENSE),
                ts,
                o.optLong("readyAt", ts));
    }
}
