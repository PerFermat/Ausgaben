package de.spahr.ausgaben.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Sichern und Wiederherstellen: sammelt Datenbank und Einstellungen der App und spielt sie einzeln oder
 * zusammen wieder ein. Das Dateiformat steckt in {@link BackupArchive}, der Passwortschutz in
 * {@link BackupCrypto}.
 */
public final class BackupStore {

    /** Name der Datenbankdatei. */
    public static final String DB_NAME = "ausgaben.db";

    /**
     * Alle SharedPreferences-Dateien der App. {@code ausgaben_settings} enthält auch die Kategoriefarben,
     * {@code ausgaben_places} die Orte je Konto, {@code receipts} offene Belege und
     * {@code widget_selection} die Auswahl des Startbildschirm-Widgets. Das Server-Passwort liegt getrennt
     * (verschlüsselt) und wird nur auf Wunsch mitgesichert.
     */
    private static final String[] PREFS_FILES = {
            "ausgaben_settings", "ausgaben_places", "receipts", "widget_selection"};

    private BackupStore() {
    }

    /**
     * Baut die Sicherungsdatei. Ist {@code password} nicht leer, wird sie als Ganzes verschlüsselt.
     *
     * @param includeServerPassword Server-Passwort mitsichern (steht sonst in keiner Form in der Datei)
     */
    public static byte[] create(Context context, boolean includeServerPassword, String password)
            throws IOException, JSONException, java.security.GeneralSecurityException {
        Context app = context.getApplicationContext();
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        for (String name : PREFS_FILES) {
            prefs.put(name, PrefsCodec.toJson(app.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
        }
        if (includeServerPassword) {
            String serverPassword = new SettingsStore(app).getPassword();
            if (!serverPassword.isEmpty()) {
                prefs.put(BackupArchive.PREFS_SECRET, PrefsCodec.toJson(
                        java.util.Collections.singletonMap(BackupArchive.KEY_SERVER_PASSWORD,
                                serverPassword)));
            }
        }
        byte[] zip = BackupArchive.write(readDatabase(app), prefs, versionCode(app));
        return password == null || password.isEmpty() ? zip : BackupCrypto.encrypt(zip, password);
    }

    /** versionCode der laufenden App – steht nur zur Information im Manifest der Sicherung. */
    private static int versionCode(Context app) {
        try {
            return (int) app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Datenbank sichern – vorher den WAL-Puffer in die Datei schreiben, sonst fehlen frische Buchungen. */
    private static byte[] readDatabase(Context app) throws IOException {
        try (Cursor cp = AppDatabase.getInstance(app).getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(TRUNCATE)")) {
            cp.moveToFirst();
        }
        File dbFile = app.getDatabasePath(DB_NAME);
        byte[] out = new byte[(int) dbFile.length()];
        try (FileInputStream in = new FileInputStream(dbFile)) {
            int done = 0;
            while (done < out.length) {
                int n = in.read(out, done, out.length - done);
                if (n <= 0) {
                    break;
                }
                done += n;
            }
        }
        return out;
    }

    /** Die Datenbank durch die gesicherte ersetzen; beim nächsten Öffnen laufen die Room-Migrationen. */
    public static void restoreData(Context context, byte[] db) throws IOException {
        Context app = context.getApplicationContext();
        AppDatabase.closeInstance();
        File dbFile = app.getDatabasePath(DB_NAME);
        deleteIfExists(dbFile);
        deleteIfExists(new File(dbFile.getPath() + "-wal"));
        deleteIfExists(new File(dbFile.getPath() + "-shm"));
        try (FileOutputStream out = new FileOutputStream(dbFile)) {
            out.write(db);
        }
        AppDatabase.getInstance(app);
    }

    /**
     * Die gesicherten Einstellungen einspielen: je Datei erst leeren, dann typtreu schreiben. Ein
     * mitgesichertes Server-Passwort geht in den verschlüsselten Speicher.
     */
    public static void restoreSettings(Context context, BackupArchive.Content content)
            throws JSONException {
        Context app = context.getApplicationContext();
        for (Map.Entry<String, String> e : content.prefs.entrySet()) {
            if (BackupArchive.PREFS_SECRET.equals(e.getKey())) {
                Object pw = PrefsCodec.fromJson(e.getValue()).get(BackupArchive.KEY_SERVER_PASSWORD);
                if (pw instanceof String) {
                    new SettingsStore(app).setPassword((String) pw);
                }
                continue;
            }
            SharedPreferences prefs = app.getSharedPreferences(e.getKey(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (Map.Entry<String, Object> v : PrefsCodec.fromJson(e.getValue()).entrySet()) {
                Object value = v.getValue();
                if (value instanceof String) {
                    editor.putString(v.getKey(), (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(v.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(v.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(v.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(v.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> set = (Set<String>) value;
                    editor.putStringSet(v.getKey(), set);
                }
            }
            editor.commit();
        }
    }

    /** Enthält die Sicherung ein Server-Passwort? (Nur für den Hinweis im Wiederherstellen-Dialog.) */
    public static boolean hasServerPassword(BackupArchive.Content content) {
        String json = content.prefs(BackupArchive.PREFS_SECRET);
        if (json == null) {
            return false;
        }
        try {
            return new JSONObject(json).has(BackupArchive.KEY_SERVER_PASSWORD);
        } catch (JSONException e) {
            return false;
        }
    }

    private static void deleteIfExists(File f) {
        if (f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }
}
