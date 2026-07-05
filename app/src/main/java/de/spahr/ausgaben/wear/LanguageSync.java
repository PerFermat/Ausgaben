package de.spahr.ausgaben.wear;

import android.content.Context;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;

import java.util.Map;

import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Veröffentlicht die aktive Sprache samt der Wear-relevanten Texte als Data-Layer-DataItem, damit die Uhr
 * sie übernimmt (auch bei erneuter Verbindung, da DataItems persistent synchronisiert werden).
 */
public final class LanguageSync {

    static final String PATH = "/language";

    private LanguageSync() {
    }

    public static void publish(Context context) {
        final Context app = context.getApplicationContext();
        String code = new SettingsStore(app).getLanguage();
        new Repository(app).getWearStrings(code, strings -> {
            try {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, String> e : strings.entrySet()) {
                    o.put(e.getKey(), e.getValue());
                }
                PutDataMapRequest req = PutDataMapRequest.create(PATH);
                req.getDataMap().putString("code", code);
                req.getDataMap().putString("strings", o.toString());
                req.getDataMap().putLong("ts", System.currentTimeMillis());
                Wearable.getDataClient(app).putDataItem(req.asPutDataRequest().setUrgent());
            } catch (Exception ignored) {
            }
        });
    }
}
