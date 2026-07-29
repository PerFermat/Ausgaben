package de.spahr.ausgaben.settings;

import android.content.Context;

/**
 * Globaler Schriftgrößen-Faktor für die gesamte Handy-App. Die gewählte Stufe (siehe
 * {@code SettingsStore.FONT_SIZE_*}) wird aus {@link SettingsStore} in einen statischen Faktor geladen
 * ({@link #refresh(Context)}) und von {@code LocalizedActivity.attachBaseContext} auf die
 * {@code Configuration.fontScale} multipliziert – dadurch skalieren alle {@code sp}-Texte automatisch.
 *
 * <p>Der Faktor wirkt <b>zusätzlich</b> zur System-Schriftgröße, damit „normal" (Faktor 1,0) exakt dem
 * heutigen Verhalten entspricht und die Bedienungshilfen des Systems wirksam bleiben.</p>
 *
 * <p>Charttexte (MPAndroidChart) werden in {@code dp} gesetzt und folgen der {@code fontScale} nicht; dort
 * wird {@link #factor()} manuell multipliziert.</p>
 */
public final class FontScale {

    private static volatile float factor = 1.0f;

    private FontScale() {
    }

    /** Lädt die gewählte Schriftgröße aus den Einstellungen (synchron, nur SharedPreferences). */
    public static void refresh(Context context) {
        factor = factorFor(new SettingsStore(context.getApplicationContext()).getFontSize());
    }

    /** Aktueller Skalierungsfaktor (1,0 = normal). */
    public static float factor() {
        return factor;
    }

    private static float factorFor(String size) {
        switch (size) {
            case SettingsStore.FONT_SIZE_SMALL:
                return 0.90f;
            case SettingsStore.FONT_SIZE_LARGE:
                return 1.15f;
            case SettingsStore.FONT_SIZE_XLARGE:
                return 1.30f;
            case SettingsStore.FONT_SIZE_NORMAL:
            default:
                return 1.0f;
        }
    }
}
