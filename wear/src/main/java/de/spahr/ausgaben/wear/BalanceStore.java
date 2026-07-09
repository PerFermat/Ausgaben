package de.spahr.ausgaben.wear;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Hält den vom Phone übertragenen, fertig formatierten Standardort-Saldo (z. B. „Geldbeutel: 70,00 €") und
 * persistiert ihn (SharedPreferences). Leerer Text = kein Standardort/-konto → Anzeige ausblenden.
 */
public final class BalanceStore {

    private static final String PREFS = "wear_balance";
    private static final String KEY_TEXT = "text";

    private BalanceStore() {
    }

    /** Vom Phone empfangenen Saldo-Text speichern. */
    public static void save(Context context, String text) {
        prefs(context).edit().putString(KEY_TEXT, text == null ? "" : text).apply();
    }

    /** Zuletzt empfangener Saldo-Text („" wenn keiner). */
    public static String get(Context context) {
        return prefs(context).getString(KEY_TEXT, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
