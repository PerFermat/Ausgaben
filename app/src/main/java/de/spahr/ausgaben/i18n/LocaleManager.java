package de.spahr.ausgaben.i18n;

import android.content.Context;
import android.content.res.Configuration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.db.Language;
import de.spahr.ausgaben.db.Translation;
import de.spahr.ausgaben.db.TranslationDao;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Steuert die Mehrsprachigkeit: seedt beim ersten Start Deutsch/Englisch aus den kompilierten Ressourcen
 * in die DB und lädt die aktive Sprache in den {@link Strings}-Cache. Wird beim App-Start blockierend
 * aufgerufen, damit {@code attachBaseContext} bereits übersetzen kann.
 */
public final class LocaleManager {

    public static final String LANG_DE = "de";
    public static final String LANG_EN = "en";

    /**
     * Wear-Texte (Schlüssel, Deutsch, Englisch). Das Phone kann die Wear-Ressourcen nicht lesen, daher hier
     * fest hinterlegt – für DB, Export-Vorlage und die Sprach-Übertragung an die Uhr.
     */
    public static final String[][] WEAR = {
            {"wear_title", "Buchung erfassen", "Record booking"},
            {"wear_type_income", "Einnahme", "Income"},
            {"wear_type_transfer", "Umbuchung", "Transfer"},
            {"wear_type_expense", "Ausgabe", "Expense"},
            {"wear_cancel", "Abbrechen", "Cancel"},
            {"wear_prompt", "Buchung sagen, z. B. „Frisör 20 Euro\"", "Say a booking, e.g. \"Barber 20 euros\""},
            {"wear_listening", "Sprich jetzt…", "Speak now…"},
            {"wear_not_understood", "Nicht verstanden", "Not understood"},
            {"wear_no_mic", "Mikrofon-Berechtigung nötig", "Microphone permission needed"},
            {"wear_no_recognizer", "Keine Spracherkennung verfügbar", "No speech recognition available"},
            {"wear_pending", "%d Buchungen noch nicht übertragen", "%d bookings not yet transferred"},
    };

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private LocaleManager() {
    }

    /** Beim Start blockierend: DB seeden (falls leer) und aktive Sprache in den Cache laden. */
    public static void init(Context context) {
        run(context, true);
    }

    /** Nach einem Sprachwechsel: aktive Sprache neu in den Cache laden. */
    public static void reload(Context context) {
        run(context, false);
    }

    private static void run(Context context, boolean maySeed) {
        final Context app = context.getApplicationContext();
        Future<?> f = EXEC.submit(() -> {
            AppDatabase db = AppDatabase.getInstance(app);
            TranslationDao dao = db.translationDao();
            // Beim Start DE/EN immer neu seeden (REPLACE), damit neue Schlüssel nach App-Updates in der DB
            // und der Export-Vorlage landen. Hochgeladene Sprachen bleiben unberührt.
            if (maySeed) {
                seed(app, dao);
            }
            String lang = new SettingsStore(app).getLanguage();
            Map<String, String> m = new HashMap<>();
            // Für Zusatzsprachen zuerst Englisch als Basis, dann die aktive Sprache darüber → noch nicht
            // übersetzte Schlüssel erscheinen auf Englisch statt auf dem kompilierten Default (Deutsch).
            if (!LANG_DE.equals(lang) && !LANG_EN.equals(lang)) {
                for (TranslationDao.KeyValue kv : dao.getPairs(LANG_EN)) {
                    m.put(kv.key, kv.value);
                }
            }
            for (TranslationDao.KeyValue kv : dao.getPairs(lang)) {
                m.put(kv.key, kv.value);
            }
            Strings.set(m, toLocale(lang));
        });
        try {
            f.get();
        } catch (Exception ignored) {
        }
    }

    private static void seed(Context app, TranslationDao dao) {
        List<Translation> rows = new ArrayList<>();
        Context de = localeContext(app, Locale.GERMAN);
        Context en = localeContext(app, Locale.ENGLISH);
        for (Field field : R.string.class.getFields()) {
            try {
                int id = field.getInt(null);
                String key = field.getName();
                rows.add(new Translation(LANG_DE, key, de.getString(id)));
                rows.add(new Translation(LANG_EN, key, en.getString(id)));
            } catch (Exception ignored) {
            }
        }
        for (String[] w : WEAR) {
            rows.add(new Translation(LANG_DE, w[0], w[1]));
            rows.add(new Translation(LANG_EN, w[0], w[2]));
        }
        dao.insertAll(rows);
        dao.upsertLanguage(new Language(LANG_DE, "Deutsch"));
        dao.upsertLanguage(new Language(LANG_EN, "English"));
    }

    private static Context localeContext(Context app, Locale locale) {
        Configuration cfg = new Configuration(app.getResources().getConfiguration());
        cfg.setLocale(locale);
        return app.createConfigurationContext(cfg);
    }

    /**
     * Context in der aktuell gewählten Sprache – für {@code getString(...)} in Hintergrund-Code (z. B.
     * Export), dessen App-Context die Per-App-Sprache erst nach Neustart übernimmt. Liest die Einstellung
     * bei jedem Aufruf, greift also auch nach einem Wechsel zur Laufzeit.
     */
    public static Context localizedContext(Context base) {
        return localeContext(base.getApplicationContext(),
                toLocale(new SettingsStore(base).getLanguage()));
    }

    public static Locale toLocale(String lang) {
        if (lang == null || lang.isEmpty()) {
            return Locale.GERMAN;
        }
        return new Locale(lang);
    }
}
