package de.spahr.ausgaben.wear;

import android.content.Context;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Veröffentlicht die Konto/Ort-Salden als Data-Layer-DataItem, damit die Uhr sie unter den Knöpfen anzeigen
 * und per Wechsel-Knopf durchschalten kann. {@code text} = Standardkonto/-ort (Position 0), {@code list} =
 * alle Konten/Orte (Zeilen „account&lt;US&gt;Anzeige"). Batterie-neutral: nur bei Änderung übertragen (Data-Layer
 * dedupliziert unveränderte DataItems).
 */
public final class BalanceSync {

    static final String PATH = "/balance";
    /** Trennzeichen zwischen Konto und Anzeige (identisch zu {@code BalanceStore.SEP} auf der Uhr). */
    private static final char SEP = '\u001F';

    private BalanceSync() {
    }

    public static void publish(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                MoneyFormat.refresh(app);
                List<String[]> entries = buildEntries(app); // [account, place, "Anzeige"]
                StringBuilder list = new StringBuilder();
                for (String[] e : entries) {
                    if (list.length() > 0) {
                        list.append('\n');
                    }
                    list.append(e[0]).append(SEP).append(e[1]).append(SEP).append(e[2]);
                }
                String text = entries.isEmpty() ? "" : entries.get(0)[2];
                PutDataMapRequest req = PutDataMapRequest.create(PATH);
                req.getDataMap().putString("text", text);
                req.getDataMap().putString("list", list.toString());
                Wearable.getDataClient(app).putDataItem(req.asPutDataRequest().setUrgent());
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * Alle wählbaren Konto/Ort-Salden als {@code [account, place, "Ort: Saldo"]}; Standardkonto/-ort steht
     * vorn (Position 0). Konto ohne Orte → {@code place == ""} und Kontosaldo („Konto: Saldo").
     */
    private static List<String[]> buildEntries(Context app) {
        List<String[]> out = new ArrayList<>();
        AppDatabase db = AppDatabase.getInstance(app);
        PlacesStore ps = new PlacesStore(app);
        SettingsStore s = new SettingsStore(app);
        String defAcc = s.getDefaultAccount();
        String defPlace = defAcc.isEmpty() ? "" : ps.getDefaultPlace(defAcc);
        defPlace = defPlace == null ? "" : defPlace.trim();

        int defaultPos = -1;
        for (String account : db.accountDao().getActiveNames()) {
            String cur = Currencies.forAccount(account);
            List<String> places = ps.getPlaces(account);
            if (places == null || places.isEmpty()) {
                long cents = db.bookingDao().getBalanceByAccount(account);
                if (account.equals(defAcc) && defPlace.isEmpty()) {
                    defaultPos = out.size();
                }
                out.add(new String[]{account, "", account + ": " + MoneyFormat.display(cents, cur)});
            } else {
                for (String place : places) {
                    long cents = db.placeEntryDao().getBalance(account, place);
                    if (account.equals(defAcc) && place.equals(defPlace)) {
                        defaultPos = out.size();
                    }
                    out.add(new String[]{account, place, place + ": " + MoneyFormat.display(cents, cur)});
                }
            }
        }
        // Standardkonto/-ort nach vorn (Position 0), damit die Uhr dort standardmäßig steht.
        if (defaultPos > 0) {
            out.add(0, out.remove(defaultPos));
        }
        return out;
    }
}
