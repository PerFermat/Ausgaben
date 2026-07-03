package de.spahr.ausgaben.wear;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Empfängt von der Wear-OS-Uhr per Data Layer gesprochene Ausgaben (Pfad {@code /expense/new}), legt daraus
 * über den bestehenden Parser eine Buchung an und bestätigt den Empfang (Pfad {@code /expense/ack}).
 *
 * <p>Doppelte Verarbeitung wird über die mitgesendete ID verhindert: bereits verarbeitete IDs werden
 * gemerkt; trifft dieselbe ID erneut ein, entsteht keine zweite Buchung – es wird aber erneut bestätigt,
 * damit die Uhr den Eintrag als synchronisiert markieren kann (idempotent).</p>
 */
public class ExpenseWearListenerService extends WearableListenerService {

    private static final String TAG = "AusgabenWearListener";
    private static final String PATH_NEW = "/expense/new";
    private static final String PATH_ACK = "/expense/ack";
    private static final String PREFS = "wear_processed";
    private static final String KEY_IDS = "processed_ids";

    @Override
    public void onMessageReceived(@NonNull MessageEvent event) {
        Log.d(TAG, "onMessageReceived: " + event.getPath());
        if (!PATH_NEW.equals(event.getPath())) {
            return;
        }
        String id;
        try {
            JSONObject json = new JSONObject(new String(event.getData(), StandardCharsets.UTF_8));
            id = json.optString("id", "");
            String text = json.optString("text", "");
            String type = json.optString("type", Repository.VOICE_TYPE_EXPENSE);
            Log.d(TAG, "empfangen id=" + id + " type=" + type + " text=" + text);
            if (id.isEmpty()) {
                return;
            }
            // Nur einmal verarbeiten; bei erneuter Zustellung nur bestätigen.
            if (!isProcessed(id)) {
                if (!text.trim().isEmpty()) {
                    Repository repository = new Repository(this);
                    String defaultAccount = new SettingsStore(this).getDefaultAccount();
                    boolean created = repository.createVoiceBookingBlocking(text, defaultAccount, type);
                    Log.d(TAG, "Buchung angelegt: " + created);
                }
                markProcessed(id);
            } else {
                Log.d(TAG, "bereits verarbeitet, nur ACK");
            }
        } catch (Exception e) {
            Log.w(TAG, "Fehler beim Verarbeiten, kein ACK", e);
            return;
        }
        Log.d(TAG, "sende ACK an " + event.getSourceNodeId());
        sendAck(event.getSourceNodeId(), id);
    }

    private void sendAck(String nodeId, String id) {
        if (nodeId == null || id == null || id.isEmpty()) {
            return;
        }
        Wearable.getMessageClient(this).sendMessage(nodeId, PATH_ACK, id.getBytes(StandardCharsets.UTF_8));
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean isProcessed(String id) {
        return prefs().getStringSet(KEY_IDS, new HashSet<>()).contains(id);
    }

    private void markProcessed(String id) {
        Set<String> ids = new HashSet<>(prefs().getStringSet(KEY_IDS, new HashSet<>()));
        ids.add(id);
        prefs().edit().putStringSet(KEY_IDS, ids).apply();
    }
}
