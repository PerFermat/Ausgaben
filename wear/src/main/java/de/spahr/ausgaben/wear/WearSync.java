package de.spahr.ausgaben.wear;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Überträgt alle offenen Einträge per {@link MessageClient} an das Phone (Pfad {@code /expense/new}).
 * Ist kein Node verbunden oder schlägt das Senden fehl, bleiben die Einträge PENDING und werden später
 * erneut versucht (bei App-Start, nach Neuerfassung und bei {@code onPeerConnected}).
 */
public final class WearSync {

    private static final String TAG = "AusgabenWearSync";

    private WearSync() {
    }

    public static void syncPending(Context context) {
        Context app = context.getApplicationContext();
        List<PendingEntry> pending = new PendingStore(app).getPending();
        Log.d(TAG, "syncPending: " + pending.size() + " offene Einträge");
        if (pending.isEmpty()) {
            return;
        }
        Wearable.getNodeClient(app).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Log.d(TAG, "verbundene Nodes: " + (nodes == null ? 0 : nodes.size()));
                    if (nodes == null || nodes.isEmpty()) {
                        return; // kein Phone erreichbar → später erneut
                    }
                    MessageClient messageClient = Wearable.getMessageClient(app);
                    for (PendingEntry entry : pending) {
                        byte[] payload;
                        try {
                            payload = entry.toJson().getBytes(StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            continue;
                        }
                        for (Node node : nodes) {
                            // Duplikate (mehrere Nodes / Wiederholungen) verhindert das Phone über die ID.
                            messageClient.sendMessage(node.getId(), WearPaths.PATH_NEW, payload)
                                    .addOnSuccessListener(unused ->
                                            Log.d(TAG, "gesendet an " + node.getId() + ": " + entry.id))
                                    .addOnFailureListener(e ->
                                            Log.w(TAG, "senden fehlgeschlagen an " + node.getId(), e));
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "getConnectedNodes fehlgeschlagen", e));
    }
}
