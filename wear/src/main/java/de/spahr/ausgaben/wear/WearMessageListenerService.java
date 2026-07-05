package de.spahr.ausgaben.wear;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
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
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED
                    && WearPaths.PATH_LANGUAGE.equals(event.getDataItem().getUri().getPath())) {
                DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                String code = map.getString("code", "de");
                WearStrings.update(this, code, map.getString("strings", "{}"));
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(code));
                Log.d(TAG, "Sprache empfangen: " + code);
                sendBroadcast(new Intent(WearPaths.ACTION_LANGUAGE_CHANGED).setPackage(getPackageName()));
            }
        }
    }

    @Override
    public void onPeerConnected(@NonNull Node peer) {
        Log.d(TAG, "onPeerConnected: " + peer.getId());
        WearSync.syncPending(this);
    }
}
