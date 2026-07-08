package de.spahr.ausgaben.wear;

import android.content.Context;

/**
 * FOSS-Variante (F-Droid) ohne Google Play Services: Es gibt keine Wear-Kopplung über die Data-Layer-API,
 * daher ist die Sprach-Synchronisation zur Uhr hier ein bewusster No-op. Gleiche Signatur wie die
 * {@code full}-Variante, damit Aufrufer (z. B. SettingsActivity) in beiden Flavors unverändert bauen.
 */
public final class LanguageSync {

    private LanguageSync() {
    }

    /** No-op: In der GMS-freien Variante gibt es keine Uhr-Anbindung, an die die Sprache gesendet würde. */
    public static void publish(Context context) {
        // absichtlich leer (kein Wear Data Layer in der foss-Variante)
    }
}
