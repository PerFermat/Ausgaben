package de.spahr.ausgaben.wear;

import android.content.Context;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Veröffentlicht den Saldo des Standardorts (Standardkonto → Standardort) als Data-Layer-DataItem, damit ihn
 * die Uhr unter den Knöpfen anzeigen kann. Batterie-neutral: es wird nur der fertige Text gesendet (ohne
 * Zeitstempel), sodass ein unveränderter Saldo dank Data-Layer-Deduplizierung nicht erneut übertragen wird.
 */
public final class BalanceSync {

    static final String PATH = "/balance";

    private BalanceSync() {
    }

    public static void publish(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                String text = buildText(app);
                PutDataMapRequest req = PutDataMapRequest.create(PATH);
                req.getDataMap().putString("text", text);
                Wearable.getDataClient(app).putDataItem(req.asPutDataRequest().setUrgent());
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** „Ort: Saldo" (z. B. „Geldbeutel: 70,00 €") oder „" wenn kein Standardkonto/-ort gesetzt ist. */
    private static String buildText(Context app) {
        String account = new SettingsStore(app).getDefaultAccount();
        if (account.isEmpty()) {
            return "";
        }
        String place = new PlacesStore(app).getDefaultPlace(account);
        if (place == null || place.trim().isEmpty()) {
            return "";
        }
        place = place.trim();
        long cents = AppDatabase.getInstance(app).placeEntryDao().getBalance(account, place);
        return place + ": " + MoneyFormat.display(cents, Currencies.forAccount(account));
    }
}
