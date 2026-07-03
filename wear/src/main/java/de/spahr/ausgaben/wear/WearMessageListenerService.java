package de.spahr.ausgaben.wear;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

/**
 * Empfängt die Bestätigungen des Phones ({@code /expense/ack}) und stößt die automatische Synchronisation
 * an, sobald das Phone wieder erreichbar ist ({@code onPeerConnected}) – auch bei geschlossener App, da das
 * System den Service startet. Keine Nutzeraktion erforderlich.
 */
public class WearMessageListenerService extends WearableListenerService {

    private static final String TAG = "AusgabenWearAck";

    @Override
    public void onMessageReceived(@NonNull MessageEvent event) {
        Log.d(TAG, "onMessageReceived: " + event.getPath());
        if (WearPaths.PATH_ACK.equals(event.getPath())) {
            String id = new String(event.getData(), StandardCharsets.UTF_8);
            Log.d(TAG, "ACK erhalten für " + id);
            new PendingStore(this).remove(id);
            // offene Anzeige (falls App offen) aktualisieren
            sendBroadcast(new Intent(WearPaths.ACTION_PENDING_CHANGED).setPackage(getPackageName()));
        }
    }

    @Override
    public void onPeerConnected(@NonNull Node peer) {
        Log.d(TAG, "onPeerConnected: " + peer.getId());
        WearSync.syncPending(this);
    }
}
