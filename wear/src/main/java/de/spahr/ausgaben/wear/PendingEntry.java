package de.spahr.ausgaben.wear;

import org.json.JSONException;
import org.json.JSONObject;

/** Eine noch nicht (bestätigt) übertragene Sprach-Buchung auf der Uhr. */
public class PendingEntry {

    public final String id;
    public final String text;
    /** Buchungstyp: {@link WearPaths#TYPE_INCOME}/{@code TYPE_EXPENSE}/{@code TYPE_TRANSFER}. */
    public final String type;
    /** Zum Sprechzeitpunkt bestimmte Koordinaten „lat, lon" (leer = keine). */
    public final String gps;
    public final long timestamp;
    /** Frühester Sendezeitpunkt (lokal): erlaubt das 10-Sekunden-Abbrechen vor der Übertragung. */
    public final long readyAt;

    public PendingEntry(String id, String text, String type, String gps, long timestamp, long readyAt) {
        this.id = id;
        this.text = text;
        this.type = type;
        this.gps = gps == null ? "" : gps;
        this.timestamp = timestamp;
        this.readyAt = readyAt;
    }

    /** JSON für Speicherung + Übertragung: {"id","text","type","gps","timestamp","readyAt"}. */
    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("text", text);
        o.put("type", type);
        o.put("gps", gps);
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
                o.optString("gps", ""),
                ts,
                o.optLong("readyAt", ts));
    }
}
