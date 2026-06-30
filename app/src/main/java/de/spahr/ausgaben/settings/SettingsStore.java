package de.spahr.ausgaben.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Persistiert die App-Einstellungen. Das Nextcloud-Passwort liegt in
 * {@link EncryptedSharedPreferences} (verschlüsselt); alle übrigen Felder in normalen Prefs.
 */
public class SettingsStore {

    private static final String PREFS = "ausgaben_settings";
    private static final String SECRET_PREFS = "ausgaben_secret";

    private static final String KEY_URL = "nextcloud_url";
    private static final String KEY_USER = "nextcloud_user";
    private static final String KEY_PASSWORD = "nextcloud_password";
    private static final String KEY_FOLDER = "nextcloud_folder";
    private static final String KEY_IMPORT_FOLDER = "nextcloud_import_folder";
    private static final String KEY_DEFAULT_ACCOUNT = "default_account";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_LOCAL_EXPORT_TREE = "local_export_tree";

    private final SharedPreferences prefs;
    private final SharedPreferences secret;

    public SettingsStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.secret = createSecretPrefs(app);
        migratePlaintextPassword();
    }

    private SharedPreferences createSecretPrefs(Context app) {
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    app,
                    SECRET_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Fallback: lieber unverschlüsselt als Absturz (z. B. bei defektem Keystore)
            return app.getSharedPreferences(SECRET_PREFS + "_fallback", Context.MODE_PRIVATE);
        }
    }

    /** Einmalige Migration: früher im Klartext gespeichertes Passwort verschlüsselt übernehmen. */
    private void migratePlaintextPassword() {
        if (prefs.contains(KEY_PASSWORD)) {
            String legacy = prefs.getString(KEY_PASSWORD, "");
            if (legacy != null && !legacy.isEmpty() && getPassword().isEmpty()) {
                secret.edit().putString(KEY_PASSWORD, legacy).apply();
            }
            prefs.edit().remove(KEY_PASSWORD).apply();
        }
    }

    public String getUrl() {
        return prefs.getString(KEY_URL, "").trim();
    }

    public String getUser() {
        return prefs.getString(KEY_USER, "").trim();
    }

    public String getPassword() {
        return secret.getString(KEY_PASSWORD, "");
    }

    public boolean hasPassword() {
        return !getPassword().isEmpty();
    }

    public String getFolder() {
        return prefs.getString(KEY_FOLDER, "").trim();
    }

    public String getImportFolder() {
        return prefs.getString(KEY_IMPORT_FOLDER, "").trim();
    }

    public String getDefaultAccount() {
        return prefs.getString(KEY_DEFAULT_ACCOUNT, "").trim();
    }

    /** Persistierte SAF-Tree-URI für den lokalen Export (leer = noch nicht gewählt). */
    public String getLocalExportTree() {
        return prefs.getString(KEY_LOCAL_EXPORT_TREE, "");
    }

    public void setLocalExportTree(String uri) {
        prefs.edit().putString(KEY_LOCAL_EXPORT_TREE, uri == null ? "" : uri).apply();
    }

    public boolean hasNextcloudConfig() {
        return !getUrl().isEmpty() && !getUser().isEmpty() && hasPassword();
    }

    /** Standard: dem System folgen, bis der Nutzer aktiv umschaltet. */
    public int getNightMode() {
        return prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public boolean isDarkMode() {
        return getNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public void setNightMode(int mode) {
        prefs.edit().putInt(KEY_NIGHT_MODE, mode).apply();
    }

    /**
     * Speichert die Einstellungen. Ein leeres {@code password} lässt das vorhandene unverändert,
     * ein nicht-leeres ersetzt es (verschlüsselt).
     */
    public void save(String url, String user, String password, String folder, String importFolder,
                     String defaultAccount) {
        prefs.edit()
                .putString(KEY_URL, url == null ? "" : url.trim())
                .putString(KEY_USER, user == null ? "" : user.trim())
                .putString(KEY_FOLDER, folder == null ? "" : folder.trim())
                .putString(KEY_IMPORT_FOLDER, importFolder == null ? "" : importFolder.trim())
                .putString(KEY_DEFAULT_ACCOUNT, defaultAccount == null ? "" : defaultAccount.trim())
                .apply();
        if (password != null && !password.isEmpty()) {
            secret.edit().putString(KEY_PASSWORD, password).apply();
        }
    }
}
