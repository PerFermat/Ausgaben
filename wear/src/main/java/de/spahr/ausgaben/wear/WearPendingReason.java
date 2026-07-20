package de.spahr.ausgaben.wear;

import java.util.List;

/**
 * Warum sind noch Buchungen nicht übertragen? Reine Zustandsermittlung (kein Text), damit sowohl
 * {@link WearMainActivity} (übersetzt per {@code getString}) als auch {@link ExpenseTileService}
 * (übersetzt per {@code tr(...)}) dieselbe Logik ohne Duplikat nutzen können.
 */
public final class WearPendingReason {

    /** Nichts offen. */
    public static final int NONE = 0;
    /** Mindestens ein Eintrag wartet noch auf den Standort. */
    public static final int GPS = 1;
    /** Kein verbundener Knoten – das Phone ist nicht erreichbar. */
    public static final int NO_PHONE = 2;
    /** Liegt im Data Layer und wird gerade übertragen. */
    public static final int SENDING = 3;

    private WearPendingReason() {
    }

    public static int of(List<PendingEntry> pending, boolean phoneConnected) {
        if (pending == null || pending.isEmpty()) {
            return NONE;
        }
        long now = System.currentTimeMillis();
        for (PendingEntry e : pending) {
            if (e.readyAt > now && e.gps.isEmpty()) {
                return GPS;
            }
        }
        if (!phoneConnected) {
            return NO_PHONE;
        }
        return SENDING;
    }
}
