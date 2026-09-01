package de.spahr.ausgaben.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.ProfileManager;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Sichern und Wiederherstellen. Zwei Umfänge (siehe {@link BackupArchive}):
 *
 * <p><b>Profil-Sicherung</b> ({@link #createProfile}/{@link #restoreProfileData}/
 * {@link #restoreProfileSettings}): nur das <b>aktive Profil</b> – Datenbank und die
 * profileigenen Einstellungen (Datenquelle, Währung, Orte, gelernte Erkennungsregeln …), ohne
 * Profil-Präfix im Archiv, also auf ein beliebiges Zielprofil einspielbar. Andere Profile bleiben
 * unberührt.</p>
 *
 * <p><b>Alle-Profile-Sicherung</b> ({@link #createAll}/{@link #restoreAllData}/
 * {@link #restoreAllSettings}): die ganze Installation – Profilliste, jede Profil-Datenbank und
 * alle Einstellungsdateien 1:1.</p>
 *
 * <p>Eine Profil-Sicherung lässt sich zusätzlich gezielt aus einer Alle-Profile-Sicherung ziehen
 * ({@link #profilesInBackup}/{@link #restoreProfileFromAllBackup}): praktisch, wenn man aus einer
 * Komplettsicherung nur eines der Profile ins aktive zurückholen will.</p>
 */
public final class BackupStore {

    /**
     * Prefs-Dateien mit Profil-Präfix (siehe {@code SettingsStore}/{@code PlacesStore}/
     * {@code StatementTemplates}). Wer hier eine Datei ergänzt, muss beim Einspielen nichts
     * nachziehen.
     */
    private static final String[] PROFILE_PREFIXED_FILES = {
            "ausgaben_settings", "ausgaben_places", "ausgaben_statements"};
    /** Geräteweite Prefs-Dateien ohne Profil-Bezug – nur in einer Alle-Profile-Sicherung dabei. */
    private static final String[] GLOBAL_ONLY_FILES = {"receipts", "widget_selection"};
    private static final String PROFILES_FILE = "ausgaben_profiles";

    private BackupStore() {
    }

    // ---------------- Profil-Sicherung (ein Profil, portabel) ----------------

    /**
     * Baut eine Sicherung des <b>aktiven</b> Profils. Die Einstellungen stehen ohne Profil-Präfix im
     * Archiv, damit sich die Sicherung auf ein beliebiges Zielprofil einspielen lässt.
     */
    public static byte[] createProfile(Context context, boolean includeServerPassword, String password)
            throws IOException, JSONException, java.security.GeneralSecurityException {
        Context app = context.getApplicationContext();
        String profileId = new ProfileManager(app).getActiveProfileId();
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        for (String name : PROFILE_PREFIXED_FILES) {
            Map<String, ?> all = app.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
            prefs.put(name, PrefsCodec.toJson(extractProfileKeys(all, profileId)));
        }
        if (includeServerPassword) {
            String pw = new SettingsStore(app).getPassword();
            if (!pw.isEmpty()) {
                prefs.put(BackupArchive.PREFS_SECRET, PrefsCodec.toJson(
                        Collections.singletonMap(BackupArchive.KEY_SERVER_PASSWORD, pw)));
            }
        }
        Map<String, byte[]> dbs = Collections.singletonMap(profileId, readActiveDatabase(app));
        byte[] zip = BackupArchive.write(BackupArchive.SCOPE_PROFILE, dbs, prefs, versionCode(app));
        return password == null || password.isEmpty() ? zip : BackupCrypto.encrypt(zip, password);
    }

    /** Ersetzt die Datenbank des aktiven Profils; andere Profile bleiben unberührt. */
    public static void restoreProfileData(Context context, byte[] db) throws IOException {
        writeDatabaseFile(context, ProfileManager.currentDbFileName(context), db);
    }

    /**
     * Spielt die (unpräfizierten) Profil-Einstellungen einer Profil-Sicherung ins <b>aktive</b> Profil
     * ein – dessen bisherige Werte werden dabei ersetzt, andere Profile bleiben unangetastet.
     */
    public static void restoreProfileSettings(Context context, BackupArchive.Content content)
            throws JSONException {
        Context app = context.getApplicationContext();
        String toPrefix = "p_" + new ProfileManager(app).getActiveProfileId() + "_";
        for (Map.Entry<String, String> e : content.prefs.entrySet()) {
            if (BackupArchive.PREFS_SECRET.equals(e.getKey())) {
                Object pw = PrefsCodec.fromJson(e.getValue()).get(BackupArchive.KEY_SERVER_PASSWORD);
                if (pw instanceof String) {
                    new SettingsStore(app).setPassword((String) pw);
                }
                continue;
            }
            if (BackupArchive.PREFS_PROFILES.equals(e.getKey())) {
                continue; // eine Profil-Sicherung enthält keine Profilliste
            }
            SharedPreferences prefs = app.getSharedPreferences(e.getKey(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            removePrefixed(prefs, editor, toPrefix);
            for (Map.Entry<String, Object> v : PrefsCodec.fromJson(e.getValue()).entrySet()) {
                putTyped(editor, toPrefix + v.getKey(), v.getValue());
            }
            editor.commit();
        }
    }

    // ---------------- Alle-Profile-Sicherung ----------------

    /** Baut eine Sicherung der ganzen Installation: Profilliste, jede Datenbank, alle Einstellungen. */
    public static byte[] createAll(Context context, boolean includeServerPasswords, String password)
            throws IOException, JSONException, java.security.GeneralSecurityException {
        Context app = context.getApplicationContext();
        ProfileManager pm = new ProfileManager(app);
        String activeId = pm.getActiveProfileId();
        checkpointActiveDatabase(app);
        LinkedHashMap<String, byte[]> dbs = new LinkedHashMap<>();
        for (ProfileManager.Profile p : pm.getProfiles()) {
            if (!p.id.equals(activeId)) {
                // Andere Profile sind gerade nicht über Room geöffnet – ihr WAL-Puffer bräuchte sonst
                // einen eigenen Checkpoint, sonst fehlten frische Buchungen in der Sicherung.
                checkpointClosedDatabase(app, p.dbFileName);
            }
            dbs.put(p.id, readDatabaseFile(app, p.dbFileName));
        }
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        for (String name : PROFILE_PREFIXED_FILES) {
            prefs.put(name, PrefsCodec.toJson(app.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
        }
        for (String name : GLOBAL_ONLY_FILES) {
            prefs.put(name, PrefsCodec.toJson(app.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
        }
        prefs.put(BackupArchive.PREFS_PROFILES,
                PrefsCodec.toJson(app.getSharedPreferences(PROFILES_FILE, Context.MODE_PRIVATE).getAll()));
        if (includeServerPasswords) {
            Map<String, ?> secretAll = SettingsStore.secretPrefs(app).getAll();
            if (!secretAll.isEmpty()) {
                prefs.put(BackupArchive.PREFS_SECRET, PrefsCodec.toJson(secretAll));
            }
        }
        byte[] zip = BackupArchive.write(BackupArchive.SCOPE_ALL, dbs, prefs, versionCode(app));
        return password == null || password.isEmpty() ? zip : BackupCrypto.encrypt(zip, password);
    }

    /**
     * Ersetzt Profilliste und alle Einstellungsdateien 1:1. Am besten vor {@link #restoreAllData}
     * aufrufen, muss es aber nicht sein – die Zieldateinamen der Datenbanken werden unabhängig von der
     * Profilliste berechnet (siehe {@link ProfileManager#dbFileNameFor}).
     */
    public static void restoreAllSettings(Context context, BackupArchive.Content content) throws JSONException {
        Context app = context.getApplicationContext();
        for (Map.Entry<String, String> e : content.prefs.entrySet()) {
            SharedPreferences prefs;
            if (BackupArchive.PREFS_SECRET.equals(e.getKey())) {
                prefs = SettingsStore.secretPrefs(app);
            } else if (BackupArchive.PREFS_PROFILES.equals(e.getKey())) {
                prefs = app.getSharedPreferences(PROFILES_FILE, Context.MODE_PRIVATE);
            } else {
                prefs = app.getSharedPreferences(e.getKey(), Context.MODE_PRIVATE);
            }
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (Map.Entry<String, Object> v : PrefsCodec.fromJson(e.getValue()).entrySet()) {
                putTyped(editor, v.getKey(), v.getValue());
            }
            editor.commit();
        }
    }

    /** Ersetzt die Datenbankdateien aller in der Sicherung enthaltenen Profile. */
    public static void restoreAllData(Context context, BackupArchive.Content content) throws IOException {
        Context app = context.getApplicationContext();
        AppDatabase.closeInstance();
        for (Map.Entry<String, byte[]> e : content.dbs.entrySet()) {
            writeDatabaseFileRaw(app, ProfileManager.dbFileNameFor(e.getKey()), e.getValue());
        }
        AppDatabase.getInstance(app);
    }

    // ---------------- Ein Profil aus einer Alle-Profile-Sicherung übernehmen ----------------

    /** Profile in einer Alle-Profile-Sicherung als {id, Name}-Paare – für die Auswahl beim Einspielen. */
    public static List<String[]> profilesInBackup(BackupArchive.Content content) throws JSONException {
        List<String[]> out = new ArrayList<>();
        String json = content.prefs.get(BackupArchive.PREFS_PROFILES);
        if (json == null) {
            return out;
        }
        Object raw = PrefsCodec.fromJson(json).get("profiles");
        if (!(raw instanceof String)) {
            return out;
        }
        JSONArray arr = new JSONArray((String) raw);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new String[]{o.optString("id", ""), o.optString("name", "")});
        }
        return out;
    }

    /**
     * Zieht Datenbank und Einstellungen <b>eines</b> Profils aus einer Alle-Profile-Sicherung und
     * spielt sie ins aktive Profil ein (umbenannt vom Quell- auf das Ziel-Präfix). Andere im Archiv
     * enthaltene Profile bleiben unbeachtet.
     */
    public static void restoreProfileFromAllBackup(Context context, BackupArchive.Content content,
                                                    String sourceProfileId) throws IOException, JSONException {
        Context app = context.getApplicationContext();
        byte[] db = content.dbs.get(sourceProfileId);
        if (db != null) {
            restoreProfileData(app, db);
        }
        String fromPrefix = "p_" + sourceProfileId + "_";
        String toPrefix = "p_" + new ProfileManager(app).getActiveProfileId() + "_";
        for (String name : PROFILE_PREFIXED_FILES) {
            String json = content.prefs.get(name);
            if (json == null) {
                continue;
            }
            Map<String, Object> all = PrefsCodec.fromJson(json);
            SharedPreferences prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            removePrefixed(prefs, editor, toPrefix);
            for (Map.Entry<String, Object> e : all.entrySet()) {
                if (e.getKey().startsWith(fromPrefix)) {
                    putTyped(editor, toPrefix + e.getKey().substring(fromPrefix.length()), e.getValue());
                }
            }
            editor.commit();
        }
        String secretJson = content.prefs.get(BackupArchive.PREFS_SECRET);
        if (secretJson != null) {
            Object pw = PrefsCodec.fromJson(secretJson).get(fromPrefix + "nextcloud_password");
            if (pw instanceof String) {
                new SettingsStore(app).setPassword((String) pw);
            }
        }
    }

    /** Enthält die Sicherung (irgendein) Server-Passwort? (Nur für den Hinweis im Einspielen-Dialog.) */
    public static boolean hasServerPassword(BackupArchive.Content content) {
        String json = content.prefs(BackupArchive.PREFS_SECRET);
        if (json == null) {
            return false;
        }
        try {
            return new JSONObject(json).length() > 0;
        } catch (JSONException e) {
            return false;
        }
    }

    // ---------------- Hilfen ----------------

    private static Map<String, Object> extractProfileKeys(Map<String, ?> all, String profileId) {
        String prefix = "p_" + profileId + "_";
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    private static void removePrefixed(SharedPreferences prefs, SharedPreferences.Editor editor, String prefix) {
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith(prefix)) {
                editor.remove(k);
            }
        }
    }

    private static void putTyped(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) value;
            editor.putStringSet(key, set);
        }
    }

    private static int versionCode(Context app) {
        try {
            return (int) app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Aktive Datenbank: WAL-Puffer über die offene Room-Verbindung in die Datei schreiben. */
    private static void checkpointActiveDatabase(Context app) throws IOException {
        try (Cursor cp = AppDatabase.getInstance(app).getOpenHelper().getWritableDatabase()
                .query("PRAGMA wal_checkpoint(TRUNCATE)")) {
            cp.moveToFirst();
        }
    }

    /**
     * Ein gerade nicht geöffnetes Profil hat keine offene Room-Verbindung – kurz roh öffnen, den
     * Checkpoint fahren und wieder schließen, sonst fehlten frische Buchungen aus dessen WAL-Datei in
     * der Sicherung.
     */
    private static void checkpointClosedDatabase(Context app, String dbFileName) {
        File dbFile = app.getDatabasePath(dbFileName);
        if (!dbFile.exists()) {
            return;
        }
        try {
            SQLiteDatabase raw = SQLiteDatabase.openDatabase(
                    dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            try (Cursor cp = raw.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                cp.moveToFirst();
            } finally {
                raw.close();
            }
        } catch (Exception ignored) {
            // Kein Checkpoint möglich – dann eben mit einem eventuell noch offenen WAL sichern.
        }
    }

    private static byte[] readActiveDatabase(Context app) throws IOException {
        checkpointActiveDatabase(app);
        return readDatabaseFile(app, ProfileManager.currentDbFileName(app));
    }

    private static byte[] readDatabaseFile(Context app, String dbFileName) throws IOException {
        File dbFile = app.getDatabasePath(dbFileName);
        if (!dbFile.exists()) {
            return new byte[0];
        }
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

    /** Ersetzt die Datenbank des aktiven Profils (schließt/öffnet die Room-Instanz mit). */
    private static void writeDatabaseFile(Context context, String dbFileName, byte[] db) throws IOException {
        Context app = context.getApplicationContext();
        AppDatabase.closeInstance();
        writeDatabaseFileRaw(app, dbFileName, db);
        AppDatabase.getInstance(app);
    }

    /** Schreibt nur die Rohdatei; Aufrufer kümmert sich selbst um {@link AppDatabase}. */
    private static void writeDatabaseFileRaw(Context app, String dbFileName, byte[] db) throws IOException {
        File dbFile = app.getDatabasePath(dbFileName);
        deleteIfExists(dbFile);
        deleteIfExists(new File(dbFile.getPath() + "-wal"));
        deleteIfExists(new File(dbFile.getPath() + "-shm"));
        try (FileOutputStream out = new FileOutputStream(dbFile)) {
            out.write(db);
        }
    }

    private static void deleteIfExists(File f) {
        if (f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }
}
