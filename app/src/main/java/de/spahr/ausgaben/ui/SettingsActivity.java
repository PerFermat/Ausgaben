package de.spahr.ausgaben.ui;

import de.spahr.ausgaben.net.RemotePath;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.AusgabenApp;
import de.spahr.ausgaben.R;
import de.spahr.ausgaben.backup.BackupArchive;
import de.spahr.ausgaben.backup.BackupCrypto;
import de.spahr.ausgaben.backup.BackupStore;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.security.BiometricAuth;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

public class SettingsActivity extends LocalizedActivity implements SmbWizardController.Host {

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
    /** Assistent für SMB; ersetzt bei diesem Server-Typ die Felder URL/Benutzer/Passwort. */
    private SmbWizardController smbWizard;
    private TextInputEditText editFolder;
    private TextInputEditText editImportFolder;
    private MaterialAutoCompleteTextView editExportMode;
    private MaterialAutoCompleteTextView editServerType;
    private TextInputEditText editKmyPath;
    private MaterialAutoCompleteTextView editDefaultAccount;
    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchScheduledReminder;
    private MaterialSwitch switchAppLock;

    private MaterialAutoCompleteTextView editLanguage;
    private TextInputEditText editCurrency;
    private TextInputEditText editDividendTaxRate;
    private MaterialAutoCompleteTextView editNumberFormat;
    private MaterialAutoCompleteTextView editCsvSeparator;
    /** Aktuell gewähltes CSV-Trennzeichen (SettingsStore.CSV_SEP_*). */
    private String selectedCsvSeparator = SettingsStore.CSV_SEP_SEMICOLON;
    /** Trennzeichen-Werte passend zu den Labels in {@link #setupCsvSeparator()}. */
    private static final String[] CSV_SEPARATOR_VALUES = {
            SettingsStore.CSV_SEP_SEMICOLON, SettingsStore.CSV_SEP_COMMA};
    private com.google.android.material.slider.Slider sliderFontSize;
    /** Schriftgrößen-Werte je Slider-Position 0..3 (klein → sehr groß). */
    private static final String[] FONT_SIZE_VALUES = {
            SettingsStore.FONT_SIZE_SMALL, SettingsStore.FONT_SIZE_NORMAL,
            SettingsStore.FONT_SIZE_LARGE, SettingsStore.FONT_SIZE_XLARGE};
    private MaterialSwitch switchShowCurrency;
    private MaterialSwitch switchDividendGross;
    private MaterialSwitch switchBudgetInternal;
    /** Aktuell gewählter Zahlenformat-Wert (SettingsStore.NUMBER_FORMAT_*). */
    private String selectedNumberFormat = SettingsStore.NUMBER_FORMAT_PLAIN_COMMA;
    /** Format-Werte passend zu den angezeigten Labels in {@link #setupNumberFormat()}. */
    private static final String[] NUMBER_FORMAT_VALUES = {
            SettingsStore.NUMBER_FORMAT_DE_GROUP, SettingsStore.NUMBER_FORMAT_EN_GROUP,
            SettingsStore.NUMBER_FORMAT_PLAIN_COMMA, SettingsStore.NUMBER_FORMAT_PLAIN_DOT};
    private java.util.List<de.spahr.ausgaben.db.Language> languages = new java.util.ArrayList<>();

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    /** Antworten aus dem Sichern-Dialog – gelten bis der Dateiname gewählt ist. */
    private boolean backupIncludeServerPassword;
    private String backupPassword = "";
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
        editCsvSeparator = findViewById(R.id.editCsvSeparator);
        editDefaultAccount = findViewById(R.id.editDefaultAccount);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        smbWizard = new SmbWizardController(this, findViewById(R.id.smbWizard), settings, this);

        editUrl.setText(settings.getUrl());
        editUser.setText(settings.getUser());
        editFolder.setText(settings.getFolder());
        editImportFolder.setText(settings.getImportFolder());
        editKmyPath.setText(settings.getKmyPath());
        setupExportMode();
        setupCsvSeparator();
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

        // Tägliche Erinnerung an fällige geplante Buchungen (Standard aus).
        switchScheduledReminder = findViewById(R.id.switchScheduledReminder);
        switchScheduledReminder.setChecked(settings.isScheduledReminderEnabled());
        switchScheduledReminder.setOnCheckedChangeListener((b, checked) -> {
            settings.setScheduledReminderEnabled(checked);
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;   // Wecker erst nach der Antwort stellen
            }
            de.spahr.ausgaben.notify.ScheduledReminder.apply(this);
        });

        switchAppLock = findViewById(R.id.switchAppLock);
        switchAppLock.setChecked(settings.isAppLockEnabled());
        switchAppLock.setOnCheckedChangeListener((b, checked) -> onAppLockToggled(b, checked));

        MaterialSwitch switchGps = findViewById(R.id.switchGps);
        switchGps.setChecked(settings.isGpsEnabled());
        switchGps.setOnCheckedChangeListener((b, checked) -> settings.setGpsEnabled(checked));

        MaterialSwitch switchAmountSuggest = findViewById(R.id.switchAmountSuggest);
        switchAmountSuggest.setChecked(settings.isAmountSuggestEnabled());
        switchAmountSuggest.setOnCheckedChangeListener(
                (b, checked) -> settings.setAmountSuggestEnabled(checked));

        // Nur im full-Build (Wear-Anbindung): Offline-Sprachpaket auf der Uhr installieren lassen.
        // Der Zustand reist über LanguageSync (/language-DataItem) zur Uhr.
        MaterialSwitch switchWearModel = findViewById(R.id.switchWearOfflineModel);
        if (getResources().getBoolean(R.bool.wear_bridge)) {
            switchWearModel.setChecked(settings.isWearInstallModel());
            switchWearModel.setOnCheckedChangeListener((b, checked) -> {
                settings.setWearInstallModel(checked);
                de.spahr.ausgaben.wear.LanguageSync.publish(this);
            });
        } else {
            switchWearModel.setVisibility(android.view.View.GONE);
        }

        MaterialSwitch switchReceipt = findViewById(R.id.switchReceipt);
        switchReceipt.setChecked(settings.isReceiptEnabled());
        switchReceipt.setOnCheckedChangeListener((b, checked) -> settings.setReceiptEnabled(checked));

        MaterialSwitch switchAliasPrompt = findViewById(R.id.switchAliasPrompt);
        switchAliasPrompt.setChecked(settings.isAliasPromptEnabled());
        switchAliasPrompt.setOnCheckedChangeListener((b, checked) -> settings.setAliasPromptEnabled(checked));
        ((MaterialButton) findViewById(R.id.btnManageAliases)).setOnClickListener(
                v -> startActivity(new android.content.Intent(this, AliasActivity.class)));

        editLanguage = findViewById(R.id.editLanguage);
        editCurrency = findViewById(R.id.editCurrency);
        editCurrency.setText(settings.getCurrency());
        editNumberFormat = findViewById(R.id.editNumberFormat);
        sliderFontSize = findViewById(R.id.sliderFontSize);
        switchShowCurrency = findViewById(R.id.switchShowCurrency);
        switchShowCurrency.setChecked(settings.isCurrencyShown());
        switchDividendGross = findViewById(R.id.switchDividendGross);
        switchDividendGross.setChecked(settings.isDividendGross());
        editDividendTaxRate = findViewById(R.id.editDividendTaxRate);
        // Ziffern und das oben eingestellte Dezimalzeichen – android:inputType="numberDecimal" kennt
        // nur den Punkt und verschluckte ein Komma (siehe AmountField).
        AmountField.preparePercent(editDividendTaxRate);
        double taxPercent = settings.getDividendTaxPercent();
        if (taxPercent > 0) {
            editDividendTaxRate.setText(
                    de.spahr.ausgaben.settings.MoneyFormat.decimal(taxPercent, 0, 5));
        }
        switchBudgetInternal = findViewById(R.id.switchBudgetInternal);
        switchBudgetInternal.setChecked(settings.isBudgetInternal());
        ((MaterialButton) findViewById(R.id.btnBudgetCompute)).setOnClickListener(v -> {
            int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            // Der Lauf beginnt mit deleteYear(y) – von Hand gepflegte Budgetwerte des Jahres sind danach
            // weg, ohne Rückgängig. Deshalb erst fragen, und das betroffene Jahr benennen.
            AppDialog.destructive(this)
                    .setTitle(R.string.budget_compute_confirm_title)
                    .setMessage(getString(R.string.budget_compute_confirm_message, y))
                    .setPositiveButton(R.string.budget_compute, (d, w) ->
                            repository.computeBudgetFromHistory(y, () ->
                                    Toast.makeText(this, R.string.budget_import_done,
                                            Toast.LENGTH_LONG).show()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        ((MaterialButton) findViewById(R.id.btnBudgetImport)).setOnClickListener(v -> {
            int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            BudgetImportFlow.run(this, settings, repository, y, null);
        });
        setupNumberFormat();
        setupFontSize();
        setupLanguages();
        ((MaterialButton) findViewById(R.id.btnExportTemplate)).setOnClickListener(
                v -> templateExportLauncher.launch("ausgaben-language-template.json"));
        ((MaterialButton) findViewById(R.id.btnUploadLanguage)).setOnClickListener(
                v -> languageUploadLauncher.launch(new String[]{"application/json"}));

        repository.getAccountNames(names -> PickerAdapters.accounts(repository, editDefaultAccount, names));

        registerLaunchers();

        ((MaterialButton) findViewById(R.id.btnTestConnection)).setOnClickListener(v -> testConnection());
        ((MaterialButton) findViewById(R.id.btnSmbDiagnose)).setOnClickListener(v -> runSmbDiagnostics());
        findViewById(R.id.btnSmbSearch).setOnClickListener(v -> {
            smbWizard.restart();
            applyServerTypeHints();
        });
        ((MaterialButton) findViewById(R.id.btnBrowseKmy)).setOnClickListener(v -> browseKmy());
        ((MaterialButton) findViewById(R.id.btnBrowseFolder)).setOnClickListener(v -> browseFolderInto(editFolder));
        ((MaterialButton) findViewById(R.id.btnBrowseImportFolder)).setOnClickListener(v -> browseFolderInto(editImportFolder));

        MaterialButton btnSave = findViewById(R.id.btnSaveSettings);
        btnSave.setOnClickListener(v -> save());
        ((MaterialButton) findViewById(R.id.btnExportAll)).setOnClickListener(v -> exportAll());
        ((MaterialButton) findViewById(R.id.btnBackup)).setOnClickListener(v -> askBackupOptions());
        ((MaterialButton) findViewById(R.id.btnRestore)).setOnClickListener(v -> confirmRestore());
        ((MaterialButton) findViewById(R.id.btnDeleteAccount)).setOnClickListener(v -> manageAccounts());
        ((MaterialButton) findViewById(R.id.btnReset)).setOnClickListener(v -> confirmReset());

        setupPlaces();
    }

    private String selectedExportMode = SettingsStore.MODE_CSV;

    /** Dropdown „Export-/Import-Format" mit zwei Optionen (CSV / KMyMoney-.kmy). */
    private void setupExportMode() {
        String csvLabel = getString(R.string.export_mode_csv);
        String kmyLabel = getString(R.string.export_mode_kmy);
        PickerAdapters.plain(editExportMode, java.util.Arrays.asList(csvLabel, kmyLabel));
        selectedExportMode = settings.getExportMode();
        editExportMode.setText(
                SettingsStore.MODE_KMY.equals(selectedExportMode) ? kmyLabel : csvLabel, false);
        applyExportModeVisibility();
        editExportMode.setOnItemClickListener((parent, view, position, id) -> {
            selectedExportMode = position == 1 ? SettingsStore.MODE_KMY : SettingsStore.MODE_CSV;
            applyExportModeVisibility();
        });
    }

    /** Dropdown „CSV-Trennzeichen" (nur im CSV-Block sichtbar): Semikolon (Standard) oder Komma. */
    private void setupCsvSeparator() {
        String[] labels = {
                getString(R.string.csv_separator_semicolon),
                getString(R.string.csv_separator_comma)};
        PickerAdapters.plain(editCsvSeparator, java.util.Arrays.asList(labels));
        selectedCsvSeparator = settings.getCsvSeparator();
        int idx = SettingsStore.CSV_SEP_COMMA.equals(selectedCsvSeparator) ? 1 : 0;
        editCsvSeparator.setText(labels[idx], false);
        editCsvSeparator.setOnItemClickListener((parent, view, position, id) ->
                selectedCsvSeparator = CSV_SEPARATOR_VALUES[position]);
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
        PickerAdapters.plain(editServerType, java.util.Arrays.asList(ncLabel, davLabel, smbLabel));
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

    /**
     * Passt die URL-/Benutzer-Hinweise an den Server-Typ an (SMB nutzt smb://Host/Freigabe + Gast) und
     * zeigt bei SMB statt der Felder den Einrichtungsassistenten – außer der Benutzer hat dort
     * „Server manuell eingeben" gewählt.
     */
    private void applyServerTypeHints() {
        boolean smb = SettingsStore.SERVER_SMB.equals(selectedServerType);
        urlLayout.setHint(getString(smb ? R.string.smb_url_hint : R.string.nextcloud_url_hint));
        userLayout.setHint(getString(smb ? R.string.smb_user_hint : R.string.nextcloud_user_hint));
        if (!smb) {
            smbWizard.resetManual();
        }
        boolean wizard = smb && !smbWizard.isManual();
        int fields = wizard ? View.GONE : View.VISIBLE;
        urlLayout.setVisibility(fields);
        userLayout.setVisibility(fields);
        passwordLayout.setVisibility(fields);
        findViewById(R.id.btnTestConnection).setVisibility(fields);
        // Die Diagnose gilt der gespeicherten Verbindung – sie hilft auch (gerade) im Assistenten.
        findViewById(R.id.btnSmbDiagnose).setVisibility(smb ? View.VISIBLE : View.GONE);
        // Rückweg zum Assistenten nur, solange SMB gewählt und gerade manuell eingegeben wird.
        findViewById(R.id.btnSmbSearch).setVisibility(smb && !wizard ? View.VISIBLE : View.GONE);
        smbWizard.setVisible(wizard);
    }

    @Override
    protected void onDestroy() {
        smbWizard.stopDiscovery();
        super.onDestroy();
    }

    @Override
    public void onSmbConfigured(String url, String user, String password) {
        editUrl.setText(url);
        editUser.setText(user);
        if (!password.isEmpty()) {
            editPassword.setText(password);
        }
        String defaultAccount = editDefaultAccount.getText() == null
                ? "" : editDefaultAccount.getText().toString().trim();
        settings.save(url, user, password, textOf(editFolder), textOf(editImportFolder),
                defaultAccount, selectedExportMode, textOf(editKmyPath), SettingsStore.SERVER_SMB);
    }

    @Override
    public void onSmbManualRequested() {
        applyServerTypeHints();
    }

    /**
     * Grund einer fehlgeschlagenen Server-Aktion. Bei SMB dieselben klaren Meldungen wie im Assistenten
     * („Server nicht erreichbar", „Zugriff auf den Ordner wurde verweigert" …) statt der rohen
     * smbj-Texte; für WebDAV/Nextcloud bleibt es beim bisherigen Text.
     */
    private String serverError(Exception e) {
        if (SettingsStore.SERVER_SMB.equals(selectedServerType)) {
            return de.spahr.ausgaben.net.smb.SmbErrors.messageFor(this,
                    de.spahr.ausgaben.net.smb.SmbErrors.Step.FOLDER, e);
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
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
                final String msg = serverError(e);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * SMB-Diagnose: läuft die ganze Kette in einer Anmeldung durch und zeigt je Schritt Ergebnis und
     * rohen Statuscode. Der Bericht lässt sich in die Zwischenablage kopieren – er enthält kein
     * Passwort und ist zum Weiterschicken gedacht.
     */
    private void runSmbDiagnostics() {
        String pw = textOf(editPassword);
        // Geprüft wird der Ordner, in den die App wirklich schreibt: im .kmy-Modus der Ordner der
        // Datei (samt Datei), im CSV-Modus der Export-Ordner.
        boolean kmy = SettingsStore.MODE_KMY.equals(selectedExportMode);
        String path = kmy ? textOf(editKmyPath) : textOf(editFolder);
        SmbDiagnosticsDialog.run(this, textOf(editUrl), textOf(editUser),
                pw.isEmpty() ? settings.getPassword() : pw,
                kmy ? RemotePath.folderOf(path) : path, kmy ? RemotePath.fileOf(path) : "");
    }

    /** Öffnet den .kmy-Datei-Browser im Ordner des aktuellen kmy-Pfads (navigierbar in Unterordner). */
    private void browseKmy() {
        browseKmyAt(RemotePath.folderOf(textOf(editKmyPath)));
    }

    /**
     * Listet Unterordner und .kmy-Dateien im {@code folder} und zeigt sie in einem navigierbaren Dialog:
     * „..“ (eine Ebene hoch), dann Ordner, dann Dateien. Zugangsdaten werden je Aufruf aus den Feldern
     * gelesen.
     */
    private void browseKmyAt(String folder) {
        final String serverType = selectedServerType;
        final String url = textOf(editUrl);
        final String user = textOf(editUser);
        String pw = textOf(editPassword);
        final String password = pw.isEmpty() ? settings.getPassword() : pw;
        Toast.makeText(this, R.string.loading_files, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Ordner und Dateien in einem Aufruf: SMB meldet sich sonst zweimal hintereinander an.
                RemoteStorage.Entries entries = RemoteStorage.from(serverType, url, user, password)
                        .listEntries(folder, "kmy");
                List<String> folders = entries.folders;
                List<String> files = entries.files;
                java.util.Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> {
                    if (folder.isEmpty() && folders.isEmpty() && files.isEmpty()) {
                        Toast.makeText(this, R.string.kmy_browse_none, Toast.LENGTH_LONG).show();
                    } else {
                        showKmyPick(folder, folders, files);
                    }
                });
            } catch (Exception e) {
                final String msg = serverError(e);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showKmyPick(String folder, List<String> folders, List<String> files) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (!folder.isEmpty()) {
            labels.add("↑  ..");
            actions.add(() -> browseKmyAt(RemotePath.parentFolder(folder)));
        }
        for (String d : folders) {
            labels.add("📁  " + d);
            final String target = folder.isEmpty() ? d : folder + "/" + d;
            actions.add(() -> browseKmyAt(target));
        }
        for (String f : files) {
            labels.add(f);
            final String path = folder.isEmpty() ? f : folder + "/" + f;
            actions.add(() -> editKmyPath.setText(path));
        }
        String title = folder.isEmpty() ? getString(R.string.kmy_browse) : "/" + folder;
        new AppDialog(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    /**
     * Öffnet einen navigierbaren Ordner-Dialog (nur Ordner, keine Dateien) und schreibt den gewählten
     * Ordner in {@code target}. Nutzt – wie der kmy-Browser – {@link RemoteStorage}, gilt also für
     * Nextcloud, WebDAV und SMB. Ausgangspunkt ist der aktuell im Feld eingetragene Ordner.
     */
    private void browseFolderInto(TextInputEditText target) {
        browseFolderAt(textOf(target).trim(), target);
    }

    private void browseFolderAt(String folder, TextInputEditText target) {
        final String serverType = selectedServerType;
        final String url = textOf(editUrl);
        final String user = textOf(editUser);
        String pw = textOf(editPassword);
        final String password = pw.isEmpty() ? settings.getPassword() : pw;
        Toast.makeText(this, R.string.loading_files, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                RemoteStorage storage = RemoteStorage.from(serverType, url, user, password);
                List<String> folders = storage.listFolders(folder);
                java.util.Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> showFolderPick(folder, folders, target));
            } catch (Exception e) {
                final String msg = serverError(e);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showFolderPick(String folder, List<String> folders, TextInputEditText target) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.folder_choose_this));
        actions.add(() -> target.setText(folder));
        if (!folder.isEmpty()) {
            labels.add("↑  ..");
            actions.add(() -> browseFolderAt(RemotePath.parentFolder(folder), target));
        }
        for (String d : folders) {
            labels.add("📁  " + d);
            final String next = folder.isEmpty() ? d : folder + "/" + d;
            actions.add(() -> browseFolderAt(next, target));
        }
        String title = folder.isEmpty() ? getString(R.string.folder_browse) : "/" + folder;
        new AppDialog(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
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
            PickerAdapters.accounts(repository, editPlacesAccount, names);
            editPlacesAccount.setText(placesAccount, false);
            refreshPlaces();
        });
        // Über PickerBehaviour und nicht über setOnItemClickListener: der Name kann auch getippt und
        // stehengelassen werden, dann fällt kein Antippen eines Listeneintrags an.
        PickerBehaviour.onCommitted(editPlacesAccount, value -> {
            placesAccount = value;
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

        PickerBehaviour.onCommitted(editDefaultPlace, value ->
                placesStore.setDefaultPlace(placesAccount,
                        PlacesStore.NO_PLACE.equals(value) ? "" : value));

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
            // Stift voran, damit erkennbar ist, dass der Name antippbar ist – sonst ist der rote
            // „Entfernen"-Knopf daneben die einzige sichtbare Aktion der Zeile.
            name.setText("\u270E  " + place);
            name.setTextSize(16f);
            name.setPadding(0, 24, 0, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(lp);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            name.setBackgroundResource(ripple.resourceId);
            name.setContentDescription(getString(R.string.place_rename_hint, place));
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
        PickerAdapters.places(editDefaultPlace, options);
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
        new AppDialog(this)
                .setTitle(R.string.place_rename_title)
                .setView(frame)
                .setPositiveButton(R.string.rename, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        placesStore.renamePlace(placesAccount, oldName, newName);
                        repository.renamePlaceEntries(placesAccount, oldName, newName, this::refreshPlaces);
                    }
                })
                .show();
    }

    private void confirmRemovePlace(String place) {
        AppDialog.destructive(this)
                .setTitle(R.string.place_remove_title)
                .setMessage(getString(R.string.place_remove_message, place))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    placesStore.removePlace(placesAccount, place);
                    repository.deletePlaceEntries(placesAccount, place, this::refreshPlaces);
                })
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
                new AppDialog(this)
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
        // Benachrichtigungs-Berechtigung (ab Android 13) für die Erinnerung an fällige Buchungen.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        de.spahr.ausgaben.notify.ScheduledReminder.apply(this);
                    } else {
                        settings.setScheduledReminderEnabled(false);
                        switchScheduledReminder.setChecked(false);
                        Toast.makeText(this, R.string.reminder_no_permission, Toast.LENGTH_LONG).show();
                    }
                });
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

    // ---- Zahlenformat ----

    /** Dropdown mit den vier Zahlenformat-Optionen (Beispiel-Labels); Vorauswahl aus den Einstellungen. */
    private void setupNumberFormat() {
        selectedNumberFormat = settings.getNumberFormat();
        String[] labels = {
                getString(R.string.number_format_de_group),
                getString(R.string.number_format_en_group),
                getString(R.string.number_format_plain_comma),
                getString(R.string.number_format_plain_dot)};
        PickerAdapters.plain(editNumberFormat, java.util.Arrays.asList(labels));
        for (int i = 0; i < NUMBER_FORMAT_VALUES.length; i++) {
            if (NUMBER_FORMAT_VALUES[i].equals(selectedNumberFormat)) {
                editNumberFormat.setText(labels[i], false);
                break;
            }
        }
        editNumberFormat.setOnItemClickListener((parent, view, position, id) ->
                selectedNumberFormat = NUMBER_FORMAT_VALUES[position]);
    }

    /**
     * Schriftgrößen-Slider (links klein … rechts sehr groß). Die Änderung greift – wie beim Nacht-Modus –
     * sofort: die Auswahl wird gespeichert und die aktuelle Ansicht neu aufgebaut (Live-Vorschau). Der
     * Rest der App zieht beim nächsten Anzeigen nach (siehe {@link LocalizedActivity}).
     */
    private void setupFontSize() {
        String[] labels = {
                getString(R.string.font_size_small),
                getString(R.string.font_size_normal),
                getString(R.string.font_size_large),
                getString(R.string.font_size_xlarge)};
        sliderFontSize.setValue(indexOfFontSize(settings.getFontSize()));
        sliderFontSize.setLabelFormatter(value -> labels[Math.round(value)]);
        // Beim Loslassen anwenden (nicht bei jedem Zwischenschritt), damit kein Neuaufbau-Stakkato entsteht.
        sliderFontSize.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(com.google.android.material.slider.Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(com.google.android.material.slider.Slider slider) {
                applyFontSize(FONT_SIZE_VALUES[Math.round(slider.getValue())]);
            }
        });
    }

    private int indexOfFontSize(String code) {
        for (int i = 0; i < FONT_SIZE_VALUES.length; i++) {
            if (FONT_SIZE_VALUES[i].equals(code)) {
                return i;
            }
        }
        return 1; // normal
    }

    /** Speichert die Schriftgröße und baut die Ansicht sofort neu auf (Live-Vorschau, wie Nacht-Modus). */
    private void applyFontSize(String code) {
        if (code.equals(settings.getFontSize())) {
            return;
        }
        settings.setFontSize(code);
        de.spahr.ausgaben.settings.FontScale.refresh(this);
        recreate();
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
            PickerAdapters.plain(editLanguage, java.util.Arrays.asList(names));
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
                Toast.makeText(this, getString(R.string.language_upload_failed, reason(e)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Grund einer Ausnahme für eine Meldung – nie das Wort „null", das sonst im Text landen würde. */
    private static String reason(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.isEmpty() ? String.valueOf(e) : m;
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
            Toast.makeText(this, getString(R.string.language_upload_failed, reason(e)),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * „Konto löschen/schließen": alle Konten mit Status als Mehrfachauswahl. Untere Zeile (vor „Abbrechen"):
     * „Löschen" (immer bei Auswahl) und die kontextabhängige Aktion „Schließen"/„Öffnen". „Schließen" gibt es
     * nur, wenn <b>alle</b> ausgewählten Konten Saldo 0 haben; „Öffnen", wenn alle ausgewählten geschlossen sind.
     */
    private void manageAccounts() {
        repository.getAllAccountsWithStatus(all -> {
            // Depots gehören in die Kontenverwaltung der Schublade, nicht in diese Liste: ihr Saldo
            // kommt aus Kursen, und ein Löschen hier ließe die Wertpapiere verwaist zurück.
            java.util.List<de.spahr.ausgaben.db.Account> accounts = new java.util.ArrayList<>();
            for (de.spahr.ausgaben.db.Account a : all) {
                if (!a.isDepot()) {
                    accounts.add(a);
                }
            }
            if (accounts.isEmpty()) {
                Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
                return;
            }
            repository.getAllAccountBalances(balances -> showAccountsDialog(accounts, balances));
        });
    }

    /** Mehrfachauswahl-Dialog; die Aktionsbuttons werden je nach Auswahl dynamisch ein-/ausgeblendet. */
    private void showAccountsDialog(List<de.spahr.ausgaben.db.Account> accounts, Map<String, Long> balances) {
        String[] items = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            de.spahr.ausgaben.db.Account a = accounts.get(i);
            String status = getString(a.closed
                    ? R.string.account_status_closed : R.string.account_status_active);
            items[i] = getString(R.string.account_status_line, a.name, status);
        }
        final boolean[] checked = new boolean[accounts.size()];
        final Runnable[] updater = new Runnable[1];
        // Untere Zeile (links→rechts): „Schließen"/„Öffnen" (Neutral) · „Abbrechen" (Negativ) ·
        // „Löschen" (Positiv, rot gefüllt). „Löschen" gehört auf den bestimmenden Platz: es ist das,
        // worum es hier geht. Nur eine gefüllte Taste – so bleiben alle drei nebeneinander.
        final AlertDialog dlg = AppDialog.destructive(this)
                .setTitle(R.string.account_manage_choose)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {
                    checked[which] = isChecked;
                    if (updater[0] != null) {
                        updater[0].run();
                    }
                })
                .setNeutralButton(R.string.account_close, null)
                .setPositiveButton(R.string.delete, null)
                .create();
        dlg.setOnShowListener(dialog -> {
            Button delBtn = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            Button actBtn = dlg.getButton(AlertDialog.BUTTON_NEUTRAL); // Schließen/Öffnen (dynamisch)
            updater[0] = () -> {
                List<de.spahr.ausgaben.db.Account> sel = selectedAccounts(accounts, checked);
                delBtn.setEnabled(!sel.isEmpty());
                boolean allClosed = !sel.isEmpty();
                boolean allOpenZero = !sel.isEmpty();
                for (de.spahr.ausgaben.db.Account a : sel) {
                    long bal = balances.containsKey(a.name) ? balances.get(a.name) : 0L;
                    if (!a.closed) {
                        allClosed = false;
                    }
                    if (a.closed || bal != 0) {
                        allOpenZero = false;
                    }
                }
                if (allClosed) {
                    actBtn.setText(R.string.account_reopen);
                    actBtn.setVisibility(View.VISIBLE);
                } else if (allOpenZero) {
                    actBtn.setText(R.string.account_close);
                    actBtn.setVisibility(View.VISIBLE);
                } else {
                    actBtn.setVisibility(View.GONE); // Schließen nur bei Saldo 0 aller Ausgewählten
                }
            };
            delBtn.setOnClickListener(v -> {
                List<de.spahr.ausgaben.db.Account> sel = selectedAccounts(accounts, checked);
                if (sel.isEmpty()) {
                    return;
                }
                dlg.dismiss();
                confirmDeleteAccounts(accountNames(sel));
            });
            actBtn.setOnClickListener(v -> {
                List<de.spahr.ausgaben.db.Account> sel = selectedAccounts(accounts, checked);
                if (sel.isEmpty()) {
                    return;
                }
                boolean allClosed = true;
                for (de.spahr.ausgaben.db.Account a : sel) {
                    if (!a.closed) {
                        allClosed = false;
                        break;
                    }
                }
                final List<String> names = accountNames(sel);
                final boolean reopen = allClosed;
                dlg.dismiss();
                repository.setAccountsClosed(names, !reopen, () ->
                        Toast.makeText(this, getString(reopen
                                ? R.string.accounts_reopened_done : R.string.accounts_closed_done,
                                names.size()), Toast.LENGTH_SHORT).show());
            });
            updater[0].run();
        });
        dlg.show();
    }

    /** Die aktuell angehakten Konten. */
    private List<de.spahr.ausgaben.db.Account> selectedAccounts(
            List<de.spahr.ausgaben.db.Account> accounts, boolean[] checked) {
        List<de.spahr.ausgaben.db.Account> sel = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            if (checked[i]) {
                sel.add(accounts.get(i));
            }
        }
        return sel;
    }

    private List<String> accountNames(List<de.spahr.ausgaben.db.Account> accounts) {
        List<String> names = new ArrayList<>();
        for (de.spahr.ausgaben.db.Account a : accounts) {
            names.add(a.name);
        }
        return names;
    }

    private void confirmDeleteAccounts(List<String> accounts) {
        AppDialog.destructive(this)
                .setTitle(R.string.delete_account_confirm_title)
                .setMessage(getString(R.string.delete_accounts_confirm_message, accounts.size()))
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteAccounts(accounts, () -> {
                    for (String account : accounts) {
                        placesStore.removeAccount(account);
                        if (account.equals(placesAccount)) {
                            placesAccount = settings.getDefaultAccount();
                        }
                    }
                    Toast.makeText(this, getString(R.string.accounts_deleted_done, accounts.size()),
                            Toast.LENGTH_LONG).show();
                }))
                .show();
    }

    private void confirmReset() {
        AppDialog.destructive(this)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_message)
                .setPositiveButton(R.string.reset_db, (d, w) -> repository.resetAllData(this::finishReset))
                .show();
    }

    /**
     * Nach dem Leeren der Datenbank den Rest des Auslieferungszustands herstellen: Einstellungen (inkl.
     * Server-Passwort), Orte-Definitionen, offene Belege samt Dateien und die Widget-Auswahl löschen, dann
     * die App neu starten, damit auch alle Zwischenspeicher (Währung, Zahlenformat, gewähltes Konto …)
     * frisch sind – die App steht danach wie nach der Installation da (Willkommen-Assistent).
     */
    private void finishReset() {
        settings.clearAll();
        new de.spahr.ausgaben.settings.PlacesStore(this).clearAll();
        de.spahr.ausgaben.receipt.Receipts.reset(this);
        getSharedPreferences("widget_selection", MODE_PRIVATE).edit().clear().commit();
        Toast.makeText(this, R.string.reset_done, Toast.LENGTH_LONG).show();
        android.content.Intent launch =
                getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
        }
        finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    private void confirmRestore() {
        // Alles anbieten: je nach Gerät meldet der Dateianbieter ZIP mal als application/zip, mal als
        // octet-stream, und verschlüsselte Sicherungen (.abk) haben gar keinen bekannten Typ. Ob es eine
        // Sicherung ist, entscheidet ohnehin der Dateiinhalt (Kopf bzw. Manifest).
        restoreLauncher.launch(new String[]{"*/*"});
    }

    /**
     * Sicherung einlesen: bei Bedarf Passwort abfragen, dann fragen, was eingespielt werden soll (Daten,
     * Einstellungen oder beides) und erst danach die Sicherheitsabfrage stellen.
     */
    private void doRestore(Uri uri) {
        new Thread(() -> {
            try {
                byte[] data = readBytes(uri);
                if (BackupCrypto.isEncrypted(data)) {
                    runOnUiThread(() -> askBackupPassword(data));
                    return;
                }
                openRestore(data);
            } catch (Exception e) {
                postRestoreError(e);
            }
        }).start();
    }

    /** Passwort der verschlüsselten Sicherung erfragen und die Datei damit öffnen. */
    private void askBackupPassword(byte[] data) {
        final TextInputEditText field = new TextInputEditText(this);
        field.setHint(getString(R.string.backup_password_hint));
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        new AppDialog(this)
                .setTitle(R.string.restore_password_title)
                .setView(box)
                .setPositiveButton(R.string.restore_db, (d, w) -> {
                    String pw = field.getText() == null ? "" : field.getText().toString();
                    new Thread(() -> {
                        byte[] plain;
                        try {
                            plain = BackupCrypto.decrypt(data, pw);
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, R.string.restore_password_wrong,
                                    Toast.LENGTH_LONG).show());
                            return;
                        }
                        try {
                            openRestore(plain);
                        } catch (Exception e) {
                            postRestoreError(e);
                        }
                    }).start();
                })
                .show();
    }

    /** Archiv lesen und die Auswahl anbieten (läuft im Hintergrund-Thread). */
    private void openRestore(byte[] zip) throws Exception {
        final BackupArchive.Content content =
                BackupArchive.read(zip);
        if (!content.hasData() && !content.hasSettings()) {
            runOnUiThread(() -> Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show());
            return;
        }
        runOnUiThread(() -> chooseRestoreScope(content));
    }

    /** „Daten", „Einstellungen" oder „Beides" – nur was die Sicherung auch enthält. */
    private void chooseRestoreScope(BackupArchive.Content content) {
        final List<String> labels = new ArrayList<>();
        final List<Integer> scopes = new ArrayList<>();   // 0 = Daten, 1 = Einstellungen, 2 = beides
        if (content.hasData()) {
            labels.add(getString(R.string.restore_what_data));
            scopes.add(0);
        }
        if (content.hasSettings()) {
            labels.add(getString(R.string.restore_what_settings));
            scopes.add(1);
        }
        if (content.hasData() && content.hasSettings()) {
            labels.add(getString(R.string.restore_what_both));
            scopes.add(2);
        }
        final int[] choice = {scopes.size() - 1};   // Vorgabe: der umfassendste Eintrag
        new AppDialog(this)
                .setTitle(R.string.restore_what_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), choice[0], (d, w) -> choice[0] = w)
                .setPositiveButton(R.string.restore_db, (d, w) ->
                        confirmAndRestore(content, scopes.get(choice[0])))
                .show();
    }

    private void confirmAndRestore(BackupArchive.Content content, int scope) {
        new AppDialog(this)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.restore_db, (d, w) -> applyRestore(content, scope))
                .show();
    }

    private void applyRestore(BackupArchive.Content content, int scope) {
        new Thread(() -> {
            try {
                if (scope == 0 || scope == 2) {
                    BackupStore.restoreData(this, content.db);
                }
                if (scope == 1 || scope == 2) {
                    BackupStore.restoreSettings(this, content);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show();
                    Intent i = new Intent(this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                });
            } catch (Exception e) {
                postRestoreError(e);
            }
        }).start();
    }

    private void postRestoreError(Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        runOnUiThread(() -> Toast.makeText(this,
                getString(R.string.restore_failed, msg), Toast.LENGTH_LONG).show());
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
        // Schriftgröße wird bereits beim Schieben des Sliders sofort angewendet (siehe setupFontSize()).
        settings.setCurrency(textOf(editCurrency));
        settings.setNumberFormat(selectedNumberFormat);
        settings.setCsvSeparator(selectedCsvSeparator);
        settings.setCurrencyShown(switchShowCurrency.isChecked());
        settings.setDividendGross(switchDividendGross.isChecked());
        // Der Steuersatz kommt im eingestellten Zahlenformat herein (Komma oder Punkt); ein unlesbarer
        // oder unsinniger Wert schaltet die Vorbelegung ab, statt eine falsche Steuer zu erzeugen.
        settings.setDividendTaxPercent(parsePercent(textOf(editDividendTaxRate)));
        settings.setBudgetInternal(switchBudgetInternal.isChecked());
        de.spahr.ausgaben.settings.Currencies.refresh(this);
        de.spahr.ausgaben.settings.MoneyFormat.refresh(this);

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

    /**
     * Vor dem Sichern fragen: Server-Passwort mitsichern? Und soll die Datei mit einem eigenen Passwort
     * verschlüsselt werden? Erst danach den Dateinamen wählen lassen.
     */
    private void askBackupOptions() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_backup_options, null, false);
        com.google.android.material.checkbox.MaterialCheckBox include =
                view.findViewById(R.id.backupIncludePassword);
        TextInputEditText pw = view.findViewById(R.id.backupPassword);
        TextInputEditText repeat = view.findViewById(R.id.backupPasswordRepeat);
        AlertDialog dialog =
                new AppDialog(this)
                        .setTitle(R.string.backup_options_title)
                        .setView(view)
                        .setPositiveButton(R.string.backup_db, null)   // erst prüfen, dann schließen
                        .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String p1 = textOf(pw);
                    if (!p1.equals(textOf(repeat))) {
                        Toast.makeText(this, R.string.backup_password_mismatch, Toast.LENGTH_LONG).show();
                        return;
                    }
                    backupIncludeServerPassword = include.isChecked();
                    backupPassword = p1;
                    dialog.dismiss();
                    backupLauncher.launch("ausgaben-backup-" + timestamp() + (p1.isEmpty() ? ".zip" : ".abk"));
                }));
        dialog.show();
    }

    private void doBackup(Uri uri) {
        new Thread(() -> {
            try {
                byte[] file = BackupStore.create(this,
                        backupIncludeServerPassword, backupPassword);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(file);
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

    /**
     * Der Steuersatz in Prozent, im eingestellten Zahlenformat eingegeben. Komma wie Punkt werden
     * angenommen – welches Zeichen das Feld zulässt, steht in den Einstellungen, aber ein von früher
     * stehengebliebener Wert soll nicht am Trennzeichen scheitern. Leer, unlesbar oder außerhalb von
     * 0 bis unter 100 ergibt 0 – dann belegt die Wertpapier-Erfassung die Steuer eben nicht vor.
     */
    private static double parsePercent(String raw) {
        String t = raw == null ? "" : raw.trim().replace(',', '.');
        if (t.isEmpty()) {
            return 0;
        }
        try {
            double v = Double.parseDouble(t);
            return v > 0 && v < 100 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
}
