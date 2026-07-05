package de.spahr.ausgaben.wear;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

/**
 * Legt alle sendebereiten Einträge als persistente {@link DataClient}-DataItems unter
 * {@code /expense/new/<id>} ab. Der Data Layer synchronisiert sie automatisch, sobald das Phone erreichbar
 * ist (auch nach Verbindungsabriss oder wenn die Phone-App geschlossen war) – ohne dass die Uhr wach bleiben
 * oder pollen muss. Das Phone verarbeitet den DataItem und löscht ihn; die Löschung räumt die Warteschlange.
 */
public final class WearSync {

    private static final String TAG = "AusgabenWearSync";

    private WearSync() {
    }

    public static void syncPending(Context context) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        List<PendingEntry> ready = new java.util.ArrayList<>();
        for (PendingEntry e : new PendingStore(app).getPending()) {
            if (e.readyAt <= now) { // vor Ablauf der 10s (readyAt in der Zukunft) noch nicht senden
                ready.add(e);
            }
        }
        Log.d(TAG, "syncPending: " + ready.size() + " sendebereite Einträge");
        if (ready.isEmpty()) {
            return;
        }
        DataClient dataClient = Wearable.getDataClient(app);
        for (PendingEntry entry : ready) {
            String payload;
            try {
                payload = entry.toJson();
            } catch (Exception e) {
                continue;
            }
            PutDataMapRequest req = PutDataMapRequest.create(WearPaths.PATH_NEW_PREFIX + entry.id);
            req.getDataMap().putString("payload", payload);
            req.getDataMap().putString("id", entry.id);
            dataClient.putDataItem(req.asPutDataRequest().setUrgent())
                    .addOnSuccessListener(unused -> Log.d(TAG, "DataItem abgelegt: " + entry.id))
                    .addOnFailureListener(e -> Log.w(TAG, "putDataItem fehlgeschlagen: " + entry.id, e));
        }
    }
}
