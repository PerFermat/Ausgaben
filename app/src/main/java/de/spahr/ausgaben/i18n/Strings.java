package de.spahr.ausgaben.i18n;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * In-Memory-Katalog der aktiven Sprache: {@code key → übersetzter Text}. Wird beim Start und bei jedem
 * Sprachwechsel aus der DB gefüllt ({@link LocaleManager}) und von der {@code Resources}-Überschreibung
 * ({@link LocaleContextWrapper}) synchron gelesen. Fehlt ein Schlüssel, greift die kompilierte Ressource.
 */
public final class Strings {

    private static volatile Map<String, String> map = Collections.emptyMap();
    private static volatile Locale locale = Locale.ENGLISH;

    private Strings() {
    }

    static void set(Map<String, String> newMap, Locale newLocale) {
        map = newMap == null ? Collections.emptyMap() : newMap;
        locale = newLocale == null ? Locale.ENGLISH : newLocale;
    }

    /** Übersetzung für den Schlüssel oder {@code null}, wenn keine hinterlegt ist. */
    public static String get(String key) {
        return map.get(key);
    }

    public static Locale locale() {
        return locale;
    }
}
