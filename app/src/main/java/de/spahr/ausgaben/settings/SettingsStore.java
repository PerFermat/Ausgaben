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
    private static final String KEY_EXPORT_MODE = "export_mode";
    private static final String KEY_KMY_PATH = "kmy_path";
    private static final String KEY_APP_LOCK = "app_lock";
    private static final String KEY_SERVER_TYPE = "server_type";
    private static final String KEY_ALIAS_PROMPT = "alias_prompt";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_CURRENCY = "currency";

    /** Server-Typ: Nextcloud (Standard, mit {@code /remote.php/dav/files/<user>/}). */
    public static final String SERVER_NEXTCLOUD = "nextcloud";
    /** Server-Typ: generischer WebDAV-Server; die Basis-URL ist bereits die DAV-Wurzel. */
    public static final String SERVER_WEBDAV = "webdav";

    /** Export-/Import-Modus: kMyMoney-CSV wie bisher. */
    public static final String MODE_CSV = "csv";
    /** Export-/Import-Modus: direkt in eine KMyMoney-.kmy-Datei. */
    public static final String MODE_KMY = "kmy";

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

    /** {@link #MODE_CSV} (Standard) oder {@link #MODE_KMY}. */
    public String getExportMode() {
        return prefs.getString(KEY_EXPORT_MODE, MODE_CSV);
    }

    public boolean isKmyMode() {
        return MODE_KMY.equals(getExportMode());
    }

    /** {@link #SERVER_NEXTCLOUD} (Standard) oder {@link #SERVER_WEBDAV}. */
    public String getServerType() {
        return prefs.getString(KEY_SERVER_TYPE, SERVER_NEXTCLOUD);
    }

    /** true = Nextcloud-Pfadschema; false = generischer WebDAV-Server (Basis-URL = DAV-Wurzel). */
    public boolean isNextcloudServer() {
        return !SERVER_WEBDAV.equals(getServerType());
    }

    /** Relativer Nextcloud-Pfad zur .kmy inkl. Dateiname, z. B. {@code KMyMoney/gdyx.kmy}. */
    public String getKmyPath() {
        return prefs.getString(KEY_KMY_PATH, "").trim();
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

    /** Optionale biometrische App-Sperre (Standard: aus). */
    public boolean isAppLockEnabled() {
        return prefs.getBoolean(KEY_APP_LOCK, false);
    }

    public void setAppLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply();
    }

    /** Nachfrage, ob ein geänderter Empfänger als Alias gemerkt werden soll (Standard: an). */
    public boolean isAliasPromptEnabled() {
        return prefs.getBoolean(KEY_ALIAS_PROMPT, true);
    }

    public void setAliasPromptEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALIAS_PROMPT, enabled).apply();
    }

    /** Sprachcode der App-Texte (Standard: Systemsprache, falls Englisch → „en", sonst „de"). */
    public String getLanguage() {
        String stored = prefs.getString(KEY_LANGUAGE, "");
        if (!stored.isEmpty()) {
            return stored;
        }
        return "en".equals(java.util.Locale.getDefault().getLanguage()) ? "en" : "de";
    }

    public void setLanguage(String code) {
        prefs.edit().putString(KEY_LANGUAGE, code == null ? "" : code.trim()).apply();
    }

    /** Globales Standard-Währungskennzeichen (für Konten ohne eigene Währung). Standard „€". */
    public String getCurrency() {
        String c = prefs.getString(KEY_CURRENCY, "€");
        return c == null || c.trim().isEmpty() ? "€" : c.trim();
    }

    public void setCurrency(String currency) {
        prefs.edit().putString(KEY_CURRENCY, currency == null ? "" : currency.trim()).apply();
    }

    /**
     * Speichert die Einstellungen. Ein leeres {@code password} lässt das vorhandene unverändert,
     * ein nicht-leeres ersetzt es (verschlüsselt).
     */
    public void save(String url, String user, String password, String folder, String importFolder,
                     String defaultAccount, String exportMode, String kmyPath, String serverType) {
        prefs.edit()
                .putString(KEY_URL, url == null ? "" : url.trim())
                .putString(KEY_USER, user == null ? "" : user.trim())
                .putString(KEY_FOLDER, folder == null ? "" : folder.trim())
                .putString(KEY_IMPORT_FOLDER, importFolder == null ? "" : importFolder.trim())
                .putString(KEY_DEFAULT_ACCOUNT, defaultAccount == null ? "" : defaultAccount.trim())
                .putString(KEY_EXPORT_MODE, MODE_KMY.equals(exportMode) ? MODE_KMY : MODE_CSV)
                .putString(KEY_KMY_PATH, kmyPath == null ? "" : kmyPath.trim())
                .putString(KEY_SERVER_TYPE, SERVER_WEBDAV.equals(serverType) ? SERVER_WEBDAV : SERVER_NEXTCLOUD)
                .apply();
        if (password != null && !password.isEmpty()) {
            secret.edit().putString(KEY_PASSWORD, password).apply();
        }
    }
}
