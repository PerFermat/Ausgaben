package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.AusgabenApp;
import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.security.BiometricAuth;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

public class SettingsActivity extends LocalizedActivity {

    private SettingsStore settings;
    private Repository repository;
    private PlacesStore placesStore;

    private LinearLayout placesContainer;
    private MaterialAutoCompleteTextView editDefaultPlace;
    private MaterialAutoCompleteTextView editPlacesAccount;
    /** Konto, dessen Orte gerade in den Einstellungen verwaltet werden. */
    private String placesAccount = "";

    private TextInputEditText editUrl;
    private TextInputEditText editUser;
    private TextInputEditText editPassword;
    private TextInputLayout urlLayout;
    private TextInputLayout userLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText editFolder;
    private TextInputEditText editImportFolder;
    private MaterialAutoCompleteTextView editExportMode;
    private MaterialAutoCompleteTextView editServerType;
    private TextInputEditText editKmyPath;
    private MaterialAutoCompleteTextView editDefaultAccount;
    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchAppLock;

    private MaterialAutoCompleteTextView editLanguage;
    private TextInputEditText editCurrency;
    private java.util.List<de.spahr.ausgaben.db.Language> languages = new java.util.ArrayList<>();

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private ActivityResultLauncher<String> templateExportLauncher;
    private ActivityResultLauncher<String[]> languageUploadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        settings = new SettingsStore(this);
        repository = new Repository(this);

        editUrl = findViewById(R.id.editUrl);
        editUser = findViewById(R.id.editUser);
        editPassword = findViewById(R.id.editPassword);
        urlLayout = findViewById(R.id.urlLayout);
        userLayout = findViewById(R.id.userLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        editFolder = findViewById(R.id.editFolder);
        editImportFolder = findViewById(R.id.editImportFolder);
        editExportMode = findViewById(R.id.editExportMode);
        editServerType = findViewById(R.id.editServerType);
        editKmyPath = findViewById(R.id.editKmyPath);
        editDefaultAccount = findViewById(R.id.editDefaultAccount);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        editUrl.setText(settings.getUrl());
        editUser.setText(settings.getUser());
        editFolder.setText(settings.getFolder());
        editImportFolder.setText(settings.getImportFolder());
        editKmyPath.setText(settings.getKmyPath());
        setupExportMode();
        setupServerType();
        editDefaultAccount.setText(settings.getDefaultAccount(), false);

        // Passwort wird nie angezeigt; nur ein Hinweis, wenn eines gespeichert ist.
        if (settings.hasPassword()) {
            passwordLayout.setHelperText(getString(R.string.password_saved_hint));
        }

        switchDarkMode.setChecked(settings.isDarkMode());
        switchDarkMode.setOnCheckedChangeListener((b, checked) -> {
            int mode = checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            settings.setNightMode(mode);
            AppCompatDelegate.setDefaultNightMode(mode);
        });

        switchAppLock = findViewById(R.id.switchAppLock);
        switchAppLock.setChecked(settings.isAppLockEnabled());
        switchAppLock.setOnCheckedChangeListener((b, checked) -> onAppLockToggled(b, checked));

        MaterialSwitch switchGps = findViewById(R.id.switchGps);
        switchGps.setChecked(settings.isGpsEnabled());
        switchGps.setOnCheckedChangeListener((b, checked) -> settings.setGpsEnabled(checked));

        MaterialSwitch switchAliasPrompt = findViewById(R.id.switchAliasPrompt);
        switchAliasPrompt.setChecked(settings.isAliasPromptEnabled());
        switchAliasPrompt.setOnCheckedChangeListener((b, checked) -> settings.setAliasPromptEnabled(checked));
        ((MaterialButton) findViewById(R.id.btnManageAliases)).setOnClickListener(
                v -> startActivity(new android.content.Intent(this, AliasActivity.class)));

        editLanguage = findViewById(R.id.editLanguage);
        editCurrency = findViewById(R.id.editCurrency);
        editCurrency.setText(settings.getCurrency());
        setupLanguages();
        ((MaterialButton) findViewById(R.id.btnExportTemplate)).setOnClickListener(
                v -> templateExportLauncher.launch("ausgaben-language-template.json"));
        ((MaterialButton) findViewById(R.id.btnUploadLanguage)).setOnClickListener(
                v -> languageUploadLauncher.launch(new String[]{"application/json"}));

        repository.getAccountNames(names -> editDefaultAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        registerLaunchers();

        ((MaterialButton) findViewById(R.id.btnTestConnection)).setOnClickListener(v -> testConnection());
        ((MaterialButton) findViewById(R.id.btnBrowseKmy)).setOnClickListener(v -> browseKmy());

        MaterialButton btnSave = findViewById(R.id.btnSaveSettings);
        btnSave.setOnClickListener(v -> save());
        ((MaterialButton) findViewById(R.id.btnExportAll)).setOnClickListener(v -> exportAll());
        ((MaterialButton) findViewById(R.id.btnBackup)).setOnClickListener(
                v -> backupLauncher.launch("ausgaben-backup-" + timestamp() + ".db"));
        ((MaterialButton) findViewById(R.id.btnRestore)).setOnClickListener(v -> confirmRestore());
        ((MaterialButton) findViewById(R.id.btnDeleteAccount)).setOnClickListener(v -> chooseAccountToDelete());
        ((MaterialButton) findViewById(R.id.btnReset)).setOnClickListener(v -> confirmReset());

        setupPlaces();
    }

    private String selectedExportMode = SettingsStore.MODE_CSV;

    /** Dropdown „Export-/Import-Format" mit zwei Optionen (CSV / KMyMoney-.kmy). */
    private void setupExportMode() {
        String csvLabel = getString(R.string.export_mode_csv);
        String kmyLabel = getString(R.string.export_mode_kmy);
        editExportMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new String[]{csvLabel, kmyLabel}));
        selectedExportMode = settings.getExportMode();
        editExportMode.setText(
                SettingsStore.MODE_KMY.equals(selectedExportMode) ? kmyLabel : csvLabel, false);
        applyExportModeVisibility();
        editExportMode.setOnItemClickListener((parent, view, position, id) -> {
            selectedExportMode = position == 1 ? SettingsStore.MODE_KMY : SettingsStore.MODE_CSV;
            applyExportModeVisibility();
        });
    }

    /** Blendet nur die zum gewählten Format passenden Felder ein (CSV: Ordner, .kmy: Dateipfad). */
    private void applyExportModeVisibility() {
        boolean kmy = SettingsStore.MODE_KMY.equals(selectedExportMode);
        findViewById(R.id.csvOptions).setVisibility(kmy ? View.GONE : View.VISIBLE);
        findViewById(R.id.kmyOptions).setVisibility(kmy ? View.VISIBLE : View.GONE);
    }

    private String selectedServerType = SettingsStore.SERVER_NEXTCLOUD;

    /** Dropdown „Server-Typ": Nextcloud (Pfadschema), generischer WebDAV oder SMB/Samba-Freigabe. */
    private void setupServerType() {
        String ncLabel = getString(R.string.server_type_nextcloud);
        String davLabel = getString(R.string.server_type_webdav);
        String smbLabel = getString(R.string.server_type_smb);
        editServerType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new String[]{ncLabel, davLabel, smbLabel}));
        selectedServerType = settings.getServerType();
        editServerType.setText(labelForServerType(ncLabel, davLabel, smbLabel), false);
        applyServerTypeHints();
        editServerType.setOnItemClickListener((parent, view, position, id) -> {
            selectedServerType = position == 1 ? SettingsStore.SERVER_WEBDAV
                    : position == 2 ? SettingsStore.SERVER_SMB : SettingsStore.SERVER_NEXTCLOUD;
            applyServerTypeHints();
        });
    }

    private String labelForServerType(String nc, String dav, String smb) {
        if (SettingsStore.SERVER_WEBDAV.equals(selectedServerType)) {
            return dav;
        }
        if (SettingsStore.SERVER_SMB.equals(selectedServerType)) {
            return smb;
        }
        return nc;
    }

    /** Passt die URL-/Benutzer-Hinweise an den Server-Typ an (SMB nutzt smb://Host/Freigabe + Gast). */
    private void applyServerTypeHints() {
        boolean smb = SettingsStore.SERVER_SMB.equals(selectedServerType);
        urlLayout.setHint(getString(smb ? R.string.smb_url_hint : R.string.nextcloud_url_hint));
        userLayout.setHint(getString(smb ? R.string.smb_user_hint : R.string.nextcloud_user_hint));
    }

    /** Verbindung mit den aktuellen (auch ungespeicherten) Feldwerten testen. */
    private void testConnection() {
        final String serverType = selectedServerType;
        final String url = textOf(editUrl);
        final String user = textOf(editUser);
        String pw = textOf(editPassword);
        final String password = pw.isEmpty() ? settings.getPassword() : pw; // leer → gespeichertes nutzen
        Toast.makeText(this, R.string.conn_testing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                RemoteStorage.from(serverType, url, user, password).testConnection();
                runOnUiThread(() -> Toast.makeText(this, R.string.conn_ok, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Listet die .kmy-Dateien im Ordner des aktuellen kmy-Pfads und lässt eine auswählen. */
    private void browseKmy() {
        final String serverType = selectedServerType;
        final String url = textOf(editUrl);
        final String user = textOf(editUser);
        String pw = textOf(editPassword);
        final String password = pw.isEmpty() ? settings.getPassword() : pw;
        final String folder = folderOf(textOf(editKmyPath));
        Toast.makeText(this, R.string.loading_files, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> files = RemoteStorage.from(serverType, url, user, password)
                        .listFiles(folder, "kmy");
                runOnUiThread(() -> {
                    if (files.isEmpty()) {
                        Toast.makeText(this, R.string.kmy_browse_none, Toast.LENGTH_LONG).show();
                    } else {
                        showKmyPick(folder, files);
                    }
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showKmyPick(String folder, List<String> files) {
        String[] items = files.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.kmy_browse)
                .setItems(items, (d, w) ->
                        editKmyPath.setText(folder.isEmpty() ? items[w] : folder + "/" + items[w]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String folderOf(String path) {
        String p = path == null ? "" : path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    // ---- Orte (Bargeld-Bestände) ----

    private void setupPlaces() {
        placesStore = new PlacesStore(this);
        placesContainer = findViewById(R.id.placesContainer);
        editDefaultPlace = findViewById(R.id.editDefaultPlace);
        editPlacesAccount = findViewById(R.id.editPlacesAccount);

        // Konto-Auswahl für die Orte-Verwaltung (Default = Standardkonto).
        placesAccount = settings.getDefaultAccount();
        repository.getAccountNames(names -> {
            if (placesAccount.isEmpty() && !names.isEmpty()) {
                placesAccount = names.get(0);
            }
            editPlacesAccount.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            editPlacesAccount.setText(placesAccount, false);
            refreshPlaces();
        });
        editPlacesAccount.setOnItemClickListener((parent, view, position, id) -> {
            placesAccount = (String) parent.getItemAtPosition(position);
            refreshPlaces();
        });

        EditText editNewPlace = findViewById(R.id.editNewPlace);
        ((MaterialButton) findViewById(R.id.btnAddPlace)).setOnClickListener(v -> {
            String name = editNewPlace.getText() == null ? "" : editNewPlace.getText().toString().trim();
            if (!name.isEmpty() && !placesAccount.isEmpty()) {
                placesStore.addPlace(placesAccount, name);
                editNewPlace.setText("");
                refreshPlaces();
            }
        });

        editDefaultPlace.setOnItemClickListener((parent, view, position, id) -> {
            String sel = (String) parent.getItemAtPosition(position);
            placesStore.setDefaultPlace(placesAccount, PlacesStore.NO_PLACE.equals(sel) ? "" : sel);
        });

        refreshPlaces();
    }

    private void refreshPlaces() {
        java.util.List<String> places = placesAccount.isEmpty()
                ? new java.util.ArrayList<>() : placesStore.getPlaces(placesAccount);

        // Zeilen je Ort (Name antippen = umbenennen, Button = entfernen)
        placesContainer.removeAllViews();
        for (String place : places) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(place);
            name.setTextSize(16f);
            name.setPadding(0, 24, 0, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(lp);
            name.setOnClickListener(v -> renamePlaceDialog(place));

            MaterialButton remove = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(R.string.remove);
            remove.setTextColor(getColor(R.color.expense_red));
            remove.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.expense_red)));
            remove.setOnClickListener(v -> confirmRemovePlace(place));

            row.addView(name);
            row.addView(remove);
            placesContainer.addView(row);
        }

        // Standardort-Dropdown (für das gewählte Konto)
        java.util.List<String> options = new java.util.ArrayList<>(places);
        options.add(PlacesStore.NO_PLACE);
        editDefaultPlace.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String def = placesAccount.isEmpty() ? "" : placesStore.getDefaultPlace(placesAccount);
        editDefaultPlace.setText(def.isEmpty() ? PlacesStore.NO_PLACE : def, false);
    }

    private void renamePlaceDialog(String oldName) {
        EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelectAllOnFocus(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.place_rename_title)
                .setView(frame)
                .setPositiveButton(R.string.rename, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        placesStore.renamePlace(placesAccount, oldName, newName);
                        repository.renamePlaceEntries(placesAccount, oldName, newName, this::refreshPlaces);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRemovePlace(String place) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.place_remove_title)
                .setMessage(getString(R.string.place_remove_message, place))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    placesStore.removePlace(placesAccount, place);
                    repository.deletePlaceEntries(placesAccount, place, this::refreshPlaces);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Schalter „App mit Biometrie schützen": bei Aktivierung Verfügbarkeit prüfen. */
    private void onAppLockToggled(CompoundButton buttonView, boolean checked) {
        if (checked) {
            String problem = BiometricAuth.availabilityMessage(this);
            if (problem == null) {
                settings.setAppLockEnabled(true);
                ((AusgabenApp) getApplication()).markUnlocked();
            } else {
                buttonView.setChecked(false); // zurücksetzen (löst erneut den Listener aus → „aus")
                new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                        .setTitle(R.string.app_lock_switch)
                        .setMessage(problem)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        } else {
            settings.setAppLockEnabled(false);
            ((AusgabenApp) getApplication()).markUnlocked();
        }
    }

    private void registerLaunchers() {
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        doBackup(uri);
                    }
                });
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        doRestore(uri);
                    }
                });
        exportTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        settings.setLocalExportTree(uri.toString());
                        runExportAll();
                    }
                });
        templateExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        writeTemplate(uri);
                    }
                });
        languageUploadLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importLanguageFile(uri);
                    }
                });
    }

    // ---- Sprache ----

    private void setupLanguages() {
        repository.getLanguages(list -> {
            languages = list;
            String[] names = new String[list.size()];
            String current = settings.getLanguage();
            String currentName = "";
            for (int i = 0; i < list.size(); i++) {
                names[i] = list.get(i).name;
                if (list.get(i).code.equals(current)) {
                    currentName = list.get(i).name;
                }
            }
            editLanguage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            if (!currentName.isEmpty()) {
                editLanguage.setText(currentName, false);
            }
            editLanguage.setOnItemClickListener((parent, view, position, id) ->
                    onLanguageChosen(languages.get(position).code));
        });
    }

    private void onLanguageChosen(String code) {
        if (code.equals(settings.getLanguage())) {
            return;
        }
        settings.setLanguage(code);
        de.spahr.ausgaben.i18n.LocaleManager.reload(this);
        de.spahr.ausgaben.wear.LanguageSync.publish(this);
        // Per-App-Sprache anwenden – erzeugt alle Activities neu (auch im Back-Stack).
        AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(code));
    }

    private void writeTemplate(Uri uri) {
        repository.buildLanguageTemplate(json -> {
            if (json == null) {
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, R.string.language_export_done, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.language_upload_failed, String.valueOf(e.getMessage())),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void importLanguageFile(Uri uri) {
        try {
            String json = new String(readBytes(uri), StandardCharsets.UTF_8);
            de.spahr.ausgaben.i18n.TranslationIo.Parsed parsed =
                    de.spahr.ausgaben.i18n.TranslationIo.parse(json);
            repository.importLanguage(parsed, () -> {
                Toast.makeText(this, getString(R.string.language_upload_done, parsed.name),
                        Toast.LENGTH_LONG).show();
                setupLanguages();
            });
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.language_upload_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** „Konto löschen": Konto wählen → bestätigen → alle Buchungen + Konto entfernen. */
    private void chooseAccountToDelete() {
        repository.getAccountNames(names -> {
            if (names.isEmpty()) {
                Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
                return;
            }
            String[] items = names.toArray(new String[0]);
            new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                    .setTitle(R.string.delete_account_choose)
                    .setItems(items, (d, w) -> confirmDeleteAccount(items[w]))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void confirmDeleteAccount(String account) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.delete_account_confirm_title)
                .setMessage(getString(R.string.delete_account_confirm_message, account))
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteAccount(account, () -> {
                    placesStore.removeAccount(account);
                    if (account.equals(placesAccount)) {
                        placesAccount = settings.getDefaultAccount();
                    }
                    Toast.makeText(this, getString(R.string.delete_account_done, account),
                            Toast.LENGTH_LONG).show();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_message)
                .setPositiveButton(R.string.reset_db, (d, w) -> repository.resetBookingData(() ->
                        Toast.makeText(this, R.string.reset_done, Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.restore_db, (d, w) ->
                        restoreLauncher.launch(new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"}))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doRestore(Uri uri) {
        new Thread(() -> {
            try {
                byte[] data = readBytes(uri);
                if (!isSqlite(data)) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show());
                    return;
                }
                AppDatabase.closeInstance();
                File dbFile = getDatabasePath("ausgaben.db");
                deleteIfExists(dbFile);
                deleteIfExists(new File(dbFile.getPath() + "-wal"));
                deleteIfExists(new File(dbFile.getPath() + "-shm"));
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(dbFile)) {
                    out.write(data);
                }
                // Neu öffnen (führt ggf. Migration aus)
                AppDatabase.getInstance(this);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show();
                    Intent i = new Intent(this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.restore_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private boolean isSqlite(byte[] data) {
        // SQLite-Magic: "SQLite format 3" gefolgt von einem Null-Byte (16 Bytes).
        byte[] header = "SQLite format 3".getBytes(StandardCharsets.US_ASCII);
        if (data.length < header.length + 1) {
            return false;
        }
        for (int i = 0; i < header.length; i++) {
            if (data[i] != header[i]) {
                return false;
            }
        }
        return data[header.length] == 0;
    }

    private void deleteIfExists(File f) {
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (is != null && (n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private void save() {
        String defaultAccount = editDefaultAccount.getText() == null
                ? "" : editDefaultAccount.getText().toString().trim();

        // Leeres Passwortfeld → vorhandenes Passwort behalten.
        settings.save(
                textOf(editUrl),
                textOf(editUser),
                textOf(editPassword),
                textOf(editFolder),
                textOf(editImportFolder),
                defaultAccount,
                selectedExportMode,
                textOf(editKmyPath),
                selectedServerType);

        repository.ensureAccount(defaultAccount);
        settings.setCurrency(textOf(editCurrency));
        de.spahr.ausgaben.settings.Currencies.refresh(this);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void exportAll() {
        if (!settings.hasRemoteConfig() && settings.getLocalExportTree().isEmpty()) {
            Toast.makeText(this, R.string.choose_export_folder, Toast.LENGTH_LONG).show();
            exportTreeLauncher.launch(null);
            return;
        }
        runExportAll();
    }

    private void runExportAll() {
        Toast.makeText(this, R.string.export_all_running, Toast.LENGTH_SHORT).show();
        String tree = settings.hasRemoteConfig() ? null : settings.getLocalExportTree();
        new ExportCoordinator(this, repository, settings, tree).exportAll(
                (message, refreshNeeded) -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void doBackup(Uri uri) {
        new Thread(() -> {
            try {
                // WAL-Checkpoint erzwingen, damit alle Daten in der Hauptdatei liegen.
                // Der Cursor muss gelesen werden, sonst führt SQLite das PRAGMA nicht aus.
                android.database.Cursor cp = AppDatabase.getInstance(this).getOpenHelper()
                        .getWritableDatabase().query("PRAGMA wal_checkpoint(TRUNCATE)");
                cp.moveToFirst();
                cp.close();
                File dbFile = getDatabasePath("ausgaben.db");
                try (FileInputStream in = new FileInputStream(dbFile);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, R.string.backup_done, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.backup_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY).format(new Date());
    }

    private String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
}
