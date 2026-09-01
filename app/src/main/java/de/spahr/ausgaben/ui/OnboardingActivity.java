package de.spahr.ausgaben.ui;

import de.spahr.ausgaben.net.RemotePath;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

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

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.backup.BackupArchive;
import de.spahr.ausgaben.backup.BackupCrypto;
import de.spahr.ausgaben.backup.BackupStore;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Language;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.CsvImporter;
import de.spahr.ausgaben.export.KmyDocument;
import de.spahr.ausgaben.export.KmyImporter;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * On-Boarding beim ersten Start (noch keine Konten): setzt die Kernpunkte (Sprache, Sync-Verbindung,
 * Import/Export-Format) und importiert direkt Konten – über <b>dieselben</b> Bausteine und denselben
 * Auswahldialog wie „Konto hinzufügen". Erscheint nur automatisch (siehe
 * {@code MainActivity.populateAccountDrawer}); es gibt bewusst keinen Menüaufruf.
 *
 * <p>Die bereits verifizierte Import-Logik in {@code MainActivity} bleibt unangetastet – dieses
 * On-Boarding importiert eigenständig, damit die kritische Bestandslogik nicht angefasst wird. Da beim
 * ersten Start noch keine Konten existieren, entfällt hier die Filterung schon vorhandener Konten.</p>
 */
public class OnboardingActivity extends LocalizedActivity implements SmbWizardController.Host {

    /**
     * Nur gesetzt, wenn dieser Assistent zum Anlegen eines <b>neuen, zusätzlichen</b> Profils gestartet
     * wurde (statt beim allerersten App-Start). Zusammen mit {@link #EXTRA_NEW_PROFILE_ID} bestimmt das,
     * auf welches Profil bei einem Abbruch zurückgewechselt wird.
     */
    public static final String EXTRA_PREVIOUS_PROFILE_ID = "previous_profile_id";
    /** Das gerade angelegte, zum Zeitpunkt des Assistenten bereits aktive, noch leere Profil. */
    public static final String EXTRA_NEW_PROFILE_ID = "new_profile_id";
    /**
     * Gesetzt von {@link #startForEditing}: unterscheidet „ein bestehendes Profil bearbeiten" (Titel
     * „Profil ändern") vom allerersten, automatisch von {@code MainActivity} gestarteten Onboarding
     * ohne jede Extra (Titel bleibt „Willkommen", da es dort noch nichts zu ändern gibt).
     */
    public static final String EXTRA_EDITING = "editing";

    private String previousProfileId;
    private String newProfileId;
    /**
     * true, solange ein Konten-Import (KMY-Depot oder CSV) im Hintergrund noch läuft. Verlässt man die
     * Maske währenddessen (Fertig/Zurück), lief die Import-Datenbankoperation nach dem Schließen der
     * Activity weiter und konnte auf eine inzwischen geschlossene Verbindung treffen (Absturz
     * „connection pool has been closed") – deshalb blockiert das Verlassen, bis der Import fertig ist.
     */
    private boolean importRunning = false;

    private Repository repository;
    private SettingsStore settings;
    private de.spahr.ausgaben.settings.ProfileManager profiles;
    private View profileColorSwatch;
    private TextInputEditText editProfileName;

    private MaterialAutoCompleteTextView editLanguage;
    private MaterialAutoCompleteTextView editExportMode;
    private MaterialAutoCompleteTextView editServerType;
    private TextInputEditText editUrl;
    private TextInputEditText editUser;
    private TextInputEditText editPassword;
    private TextInputEditText editFolder;
    private TextInputEditText editImportFolder;
    private TextInputEditText editKmyPath;
    private MaterialAutoCompleteTextView editCsvSeparator;
    /** Aktuell gewähltes CSV-Trennzeichen (SettingsStore.CSV_SEP_*). */
    private String selectedCsvSeparator = SettingsStore.CSV_SEP_SEMICOLON;
    private static final String[] CSV_SEPARATOR_VALUES = {
            SettingsStore.CSV_SEP_SEMICOLON, SettingsStore.CSV_SEP_COMMA};
    private TextInputLayout urlLayout;
    private TextInputLayout userLayout;
    private TextInputLayout passwordLayout;
    /** Assistent für SMB; ersetzt bei diesem Server-Typ die Felder URL/Benutzer/Passwort. */
    private SmbWizardController smbWizard;
    private LinearLayout importStatus;
    private View importProgress;
    private TextView importStatusText;

    private List<Language> languages = new ArrayList<>();
    private String selectedExportMode = SettingsStore.MODE_CSV;
    private String selectedServerType = SettingsStore.SERVER_NEXTCLOUD;

    // ---- Weitere Profil-Einstellungen (Währung, Dividenden, Budget, Standardkonto, Orte, Alias) ----
    private MaterialAutoCompleteTextView editDefaultAccount;
    private TextInputEditText editCurrency;
    private MaterialAutoCompleteTextView editNumberFormat;
    /** Aktuell gewählter Zahlenformat-Wert (SettingsStore.NUMBER_FORMAT_*). */
    private String selectedNumberFormat = SettingsStore.NUMBER_FORMAT_PLAIN_COMMA;
    private static final String[] NUMBER_FORMAT_VALUES = {
            SettingsStore.NUMBER_FORMAT_DE_GROUP, SettingsStore.NUMBER_FORMAT_EN_GROUP,
            SettingsStore.NUMBER_FORMAT_PLAIN_COMMA, SettingsStore.NUMBER_FORMAT_PLAIN_DOT};
    private com.google.android.material.materialswitch.MaterialSwitch switchShowCurrency;
    private com.google.android.material.materialswitch.MaterialSwitch switchDividendGross;
    private TextInputEditText editDividendTaxRate;
    private com.google.android.material.materialswitch.MaterialSwitch switchBudgetInternal;
    private com.google.android.material.materialswitch.MaterialSwitch switchAliasPrompt;

    private de.spahr.ausgaben.settings.PlacesStore placesStore;
    private LinearLayout placesContainer;
    private MaterialAutoCompleteTextView editDefaultPlace;
    private MaterialAutoCompleteTextView editPlacesAccount;
    /** Konto, dessen Orte gerade in der Profil-Maske verwaltet werden. */
    private String placesAccount = "";

    private ActivityResultLauncher<String[]> csvLauncher;
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreBackupLauncher;
    /** Antworten aus dem Sichern-Dialog – gelten bis der Dateiname gewählt ist. */
    private boolean backupIncludeServerPassword;
    private String backupPassword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        repository = new Repository(this);
        settings = new SettingsStore(this);
        profiles = new de.spahr.ausgaben.settings.ProfileManager(this);

        previousProfileId = getIntent().getStringExtra(EXTRA_PREVIOUS_PROFILE_ID);
        newProfileId = getIntent().getStringExtra(EXTRA_NEW_PROFILE_ID);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (!blockIfImporting()) {
                abortIfNewProfile();
            }
        });
        boolean editingExisting = getIntent().getBooleanExtra(EXTRA_EDITING, false);
        if (newProfileId != null) {
            toolbar.setTitle(getString(R.string.settings_profile_new));
        } else if (editingExisting) {
            toolbar.setTitle(getString(R.string.settings_profile_change));
        } else {
            // Allererstes, automatisch gestartetes Onboarding – kein Profil, zu dem „Zurück" führen
            // könnte. Titel bleibt „Willkommen" aus dem Layout.
            toolbar.setNavigationIcon(null);
        }

        setupProfileColor();
        setupCopyFromProfile();
        setupDeleteProfile();

        editLanguage = findViewById(R.id.editLanguage);
        editExportMode = findViewById(R.id.editExportMode);
        editServerType = findViewById(R.id.editServerType);
        editUrl = findViewById(R.id.editUrl);
        editUser = findViewById(R.id.editUser);
        editPassword = findViewById(R.id.editPassword);
        editFolder = findViewById(R.id.editFolder);
        editImportFolder = findViewById(R.id.editImportFolder);
        editKmyPath = findViewById(R.id.editKmyPath);
        editCsvSeparator = findViewById(R.id.editCsvSeparator);
        urlLayout = findViewById(R.id.urlLayout);
        userLayout = findViewById(R.id.userLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        importStatus = findViewById(R.id.importStatus);
        importProgress = findViewById(R.id.importProgress);
        importStatusText = findViewById(R.id.importStatusText);

        smbWizard = new SmbWizardController(this, findViewById(R.id.smbWizard), settings, this);

        setupLanguages();
        setupExportMode();
        setupCsvSeparator();
        setupServerType();
        prefillSyncFields();

        placesStore = new de.spahr.ausgaben.settings.PlacesStore(this);
        editDefaultAccount = findViewById(R.id.editDefaultAccount);
        editDefaultAccount.setText(settings.getDefaultAccount(), false);
        repository.getAccountNames(names -> PickerAdapters.accounts(repository, editDefaultAccount, names));

        editCurrency = findViewById(R.id.editCurrency);
        editCurrency.setText(settings.getCurrency());
        editNumberFormat = findViewById(R.id.editNumberFormat);
        setupNumberFormat();

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
            editDividendTaxRate.setText(de.spahr.ausgaben.settings.MoneyFormat.decimal(taxPercent, 0, 5));
        }

        switchBudgetInternal = findViewById(R.id.switchBudgetInternal);
        switchBudgetInternal.setChecked(settings.isBudgetInternal());
        ((MaterialButton) findViewById(R.id.btnBudgetCompute)).setOnClickListener(v -> {
            int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
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

        switchAliasPrompt = findViewById(R.id.switchAliasPrompt);
        switchAliasPrompt.setChecked(settings.isAliasPromptEnabled());
        ((MaterialButton) findViewById(R.id.btnManageAliases)).setOnClickListener(
                v -> startActivity(new Intent(this, AliasActivity.class)));

        setupPlaces();

        csvLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        importCsvLocal(uri);
                    }
                });
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        doBackup(uri);
                    }
                });
        restoreBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        doRestore(uri);
                    }
                });

        ((MaterialButton) findViewById(R.id.btnTestConnection))
                .setOnClickListener(v -> testConnection());
        ((MaterialButton) findViewById(R.id.btnSmbDiagnose))
                .setOnClickListener(v -> runSmbDiagnostics());
        findViewById(R.id.btnSmbSearch).setOnClickListener(v -> {
            smbWizard.restart();
            applyServerTypeHints();
        });
        ((MaterialButton) findViewById(R.id.btnBrowseKmy))
                .setOnClickListener(v -> browseKmy());
        ((MaterialButton) findViewById(R.id.btnBrowseFolder))
                .setOnClickListener(v -> browseFolderInto(editFolder));
        ((MaterialButton) findViewById(R.id.btnBrowseImportFolder))
                .setOnClickListener(v -> browseFolderInto(editImportFolder));
        ((MaterialButton) findViewById(R.id.btnImportAccounts))
                .setOnClickListener(v -> importAccounts());
        ((MaterialButton) findViewById(R.id.btnDone)).setOnClickListener(v -> {
            if (blockIfImporting()) {
                return;
            }
            saveSettings();
            finishFromProfileMask();
        });

        ((MaterialButton) findViewById(R.id.btnBackupProfile)).setOnClickListener(v -> askBackupOptions());
        ((MaterialButton) findViewById(R.id.btnRestoreProfile)).setOnClickListener(v -> confirmRestore());
        ((MaterialButton) findViewById(R.id.btnDeleteAccount)).setOnClickListener(v -> manageAccounts());
        ((MaterialButton) findViewById(R.id.btnResetProfile)).setOnClickListener(v -> confirmResetProfile());
    }

    @Override
    public void onBackPressed() {
        if (blockIfImporting()) {
            return;
        }
        abortIfNewProfile();
    }

    /**
     * Ein Konten-Import läuft im Hintergrund auf einer eigenen Datenbankverbindung weiter, auch wenn
     * diese Maske schließt. {@link #abortIfNewProfile()} wechselt dabei ggf. das aktive Profil zurück
     * und schließt die Datenbank ({@link de.spahr.ausgaben.settings.ProfileManager#switchTo}) – trifft
     * das auf einen noch laufenden Import, stürzt die App ab („connection pool has been closed").
     * Deshalb: solange {@link #importRunning}, weder „Fertig" noch Zurück zulassen.
     */
    private boolean blockIfImporting() {
        if (importRunning) {
            Toast.makeText(this, R.string.onboarding_import_running, Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    /**
     * Bricht der Nutzer die Einrichtung eines <b>neuen</b> Profils ab (Zurück-Pfeil/Systemtaste), wird
     * automatisch auf das vorherige Profil zurückgewechselt und das leere neue Profil wieder entfernt –
     * kein „Geister-Profil" ohne Inhalt. Beim allerersten App-Start (kein {@link #newProfileId}) bleibt es
     * beim einfachen {@link #finish()}.
     */
    private void abortIfNewProfile() {
        if (newProfileId != null && previousProfileId != null) {
            de.spahr.ausgaben.settings.ProfileManager pm = new de.spahr.ausgaben.settings.ProfileManager(this);
            pm.switchTo(this, previousProfileId);
            pm.deleteProfile(this, newProfileId);
        }
        finishFromProfileMask();
    }

    /**
     * Schließt diese Maske. Wurde sie über „Profil ändern"/„Neues Profil anlegen" geöffnet (statt beim
     * allerersten, automatischen Onboarding), setzt sie den Activity-Stack auf eine frische
     * {@code MainActivity} zurück – wie schon {@link ProfileSwitchDialog} es beim Wechseln/Löschen tut.
     * Nötig, weil ein Profilwechsel unterwegs die Datenbankverbindung schließt
     * ({@link de.spahr.ausgaben.settings.ProfileManager#switchTo}): ein einfaches {@link #finish()}
     * kehrte sonst zu einer darunterliegenden {@code SettingsActivity}/{@code MainActivity} zurück,
     * deren {@code Repository} noch die alte, jetzt geschlossene Verbindung hält – das stürzte beim
     * nächsten Datenbankzugriff dort ab („connection pool has been closed").
     */
    private void finishFromProfileMask() {
        if (newProfileId != null || getIntent().getBooleanExtra(EXTRA_EDITING, false)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
    }

    // ---- Dropdowns (gleiches Muster wie SettingsActivity) ----

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
        AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(code));
    }

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
            settings.setExportMode(selectedExportMode);
            applyExportModeVisibility();
        });
    }

    private void applyExportModeVisibility() {
        boolean kmy = SettingsStore.MODE_KMY.equals(selectedExportMode);
        findViewById(R.id.csvOptions).setVisibility(kmy ? View.GONE : View.VISIBLE);
        findViewById(R.id.kmyOptions).setVisibility(kmy ? View.VISIBLE : View.GONE);
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

    /** Akzentfarbe des Profils – erstes Feld der Maske. Wirkt sofort (wie die Kategoriefarben). */
    private void setupProfileColor() {
        editProfileName = findViewById(R.id.editProfileName);
        de.spahr.ausgaben.settings.ProfileManager.Profile active = profiles.getActiveProfile();
        editProfileName.setText(active != null ? active.name : "");

        profileColorSwatch = findViewById(R.id.profileColorSwatch);
        refreshProfileColorSwatch();
        View row = findViewById(R.id.profileColorRow);
        View.OnClickListener openPicker = v -> ColorPickerDialog.show(this, R.string.profile_change_color,
                de.spahr.ausgaben.settings.ProfileManager.ACCENT_PALETTE,
                color -> {
                    profiles.setAccentColor(profiles.getActiveProfileId(), color);
                    refreshProfileColorSwatch();
                    de.spahr.ausgaben.settings.AccentColor.apply(this);
                });
        profileColorSwatch.setOnClickListener(openPicker);
        row.setOnClickListener(openPicker);
    }

    private void refreshProfileColorSwatch() {
        de.spahr.ausgaben.settings.ProfileManager.Profile active = profiles.getActiveProfile();
        int color = active != null ? active.accentColor
                : de.spahr.ausgaben.settings.ProfileManager.DEFAULT_ACCENT_COLOR;
        profileColorSwatch.setBackground(ColorPickerDialog.swatchDrawable(color));
    }

    /**
     * Beim Anlegen eines <b>weiteren</b> Profils (nicht beim allerersten) lässt sich die ganze
     * Konfiguration eines bestehenden Profils übernehmen, statt sie neu einzutippen. Nur sichtbar,
     * solange es mindestens ein anderes Profil gibt.
     */
    private void setupCopyFromProfile() {
        View button = findViewById(R.id.btnCopyFromProfile);
        List<de.spahr.ausgaben.settings.ProfileManager.Profile> others = new ArrayList<>();
        String ownId = profiles.getActiveProfileId();
        if (newProfileId != null) {
            for (de.spahr.ausgaben.settings.ProfileManager.Profile p : profiles.getProfiles()) {
                if (!p.id.equals(ownId)) {
                    others.add(p);
                }
            }
        }
        if (others.isEmpty()) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) {
            labels[i] = others.get(i).name;
        }
        button.setOnClickListener(v -> new AppDialog(this)
                .setTitle(R.string.profile_copy_title)
                .setItems(labels, (d, w) -> {
                    profiles.copySettingsFrom(this, others.get(w).id, ownId);
                    prefillAll();
                })
                .show());
    }

    /** Zieht alle Feldwerte frisch aus den (ggf. gerade übernommenen) Einstellungen nach. */
    private void prefillAll() {
        refreshProfileColorSwatch();
        setupExportMode();
        setupCsvSeparator();
        setupServerType();
        prefillSyncFields();
        editDefaultAccount.setText(settings.getDefaultAccount(), false);
        editCurrency.setText(settings.getCurrency());
        setupNumberFormat();
        switchShowCurrency.setChecked(settings.isCurrencyShown());
        switchDividendGross.setChecked(settings.isDividendGross());
        double taxPercent = settings.getDividendTaxPercent();
        editDividendTaxRate.setText(taxPercent > 0
                ? de.spahr.ausgaben.settings.MoneyFormat.decimal(taxPercent, 0, 5) : "");
        switchBudgetInternal.setChecked(settings.isBudgetInternal());
        switchAliasPrompt.setChecked(settings.isAliasPromptEnabled());
        placesAccount = settings.getDefaultAccount();
        repository.getAccountNames(names -> {
            if (placesAccount.isEmpty() && !names.isEmpty()) {
                placesAccount = names.get(0);
            }
            editPlacesAccount.setText(placesAccount, false);
            refreshPlaces();
        });
    }

    /** „Profil löschen" – nur sichtbar, solange mehr als ein Profil existiert (siehe ProfileManager). */
    private void setupDeleteProfile() {
        View button = findViewById(R.id.btnDeleteProfile);
        String ownId = profiles.getActiveProfileId();
        if (profiles.getProfiles().size() <= 1) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            if (blockIfImporting()) {
                return;
            }
            AppDialog.destructive(this)
                    .setTitle(R.string.profile_delete)
                    .setMessage(R.string.profile_delete_confirm_message)
                    .setPositiveButton(R.string.profile_delete, (d, w) -> {
                        profiles.deleteProfile(this, ownId);
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

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
            settings.setServerType(selectedServerType);
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

    /** Wie in den Einstellungen: bei SMB übernimmt der Assistent die Felder URL/Benutzer/Passwort. */
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
        // Die Diagnose gilt der eingerichteten Verbindung – gerade beim Erststart ist sie das
        // Werkzeug, mit dem man überhaupt herausfindet, woran es hakt.
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
        saveSettings();
    }

    @Override
    public void onSmbManualRequested() {
        applyServerTypeHints();
    }

    private void prefillSyncFields() {
        editUrl.setText(settings.getUrl());
        editUser.setText(settings.getUser());
        editFolder.setText(settings.getFolder());
        editImportFolder.setText(settings.getImportFolder());
        editKmyPath.setText(settings.getKmyPath());
        // Passwort bleibt leer: leer speichern lässt ein vorhandenes unverändert. Ist bereits eines
        // gespeichert, unter dem Feld „••••••" anzeigen (wie in den Einstellungen).
        if (settings.hasPassword()) {
            passwordLayout.setHelperText(getString(R.string.password_saved_hint));
        }
    }

    // ---- Speichern ----

    private void saveSettings() {
        profiles.renameProfile(profiles.getActiveProfileId(), textOf(editProfileName));

        String defaultAccount = editDefaultAccount.getText() == null
                ? "" : editDefaultAccount.getText().toString().trim();
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
        settings.setCsvSeparator(selectedCsvSeparator);

        repository.ensureAccount(defaultAccount);
        settings.setCurrency(textOf(editCurrency));
        settings.setNumberFormat(selectedNumberFormat);
        settings.setCurrencyShown(switchShowCurrency.isChecked());
        settings.setDividendGross(switchDividendGross.isChecked());
        // Der Steuersatz kommt im eingestellten Zahlenformat herein (Komma oder Punkt); ein unlesbarer
        // oder unsinniger Wert schaltet die Vorbelegung ab, statt eine falsche Steuer zu erzeugen.
        settings.setDividendTaxPercent(parsePercent(textOf(editDividendTaxRate)));
        settings.setBudgetInternal(switchBudgetInternal.isChecked());
        settings.setAliasPromptEnabled(switchAliasPrompt.isChecked());
        de.spahr.ausgaben.settings.Currencies.refresh(this);
        de.spahr.ausgaben.settings.MoneyFormat.refresh(this);
        // Das Standardkonto bestimmt den Saldo, den die Uhr anzeigt – sonst zeigte sie bis zum
        // nächsten Sync noch das alte (full: echte Sync, foss: No-op-Stub).
        de.spahr.ausgaben.wear.BalanceSync.publish(this);
    }

    /**
     * Der Steuersatz in Prozent, im eingestellten Zahlenformat eingegeben. Komma wie Punkt werden
     * angenommen. Leer, unlesbar oder außerhalb von 0 bis unter 100 ergibt 0 – dann belegt die
     * Wertpapier-Erfassung die Steuer eben nicht vor.
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

    // ---- Orte (Bargeld-Bestände) ----

    private void setupPlaces() {
        placesContainer = findViewById(R.id.placesContainer);
        editDefaultPlace = findViewById(R.id.editDefaultPlace);
        editPlacesAccount = findViewById(R.id.editPlacesAccount);

        placesAccount = settings.getDefaultAccount();
        repository.getAccountNames(names -> {
            if (placesAccount.isEmpty() && !names.isEmpty()) {
                placesAccount = names.get(0);
            }
            PickerAdapters.accounts(repository, editPlacesAccount, names);
            editPlacesAccount.setText(placesAccount, false);
            refreshPlaces();
        });
        PickerBehaviour.onCommitted(editPlacesAccount, value -> {
            placesAccount = value;
            refreshPlaces();
        });

        android.widget.EditText editNewPlace = findViewById(R.id.editNewPlace);
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
                        de.spahr.ausgaben.settings.PlacesStore.NO_PLACE.equals(value) ? "" : value));

        refreshPlaces();
    }

    private void refreshPlaces() {
        List<String> places = placesAccount.isEmpty()
                ? new ArrayList<>() : placesStore.getPlaces(placesAccount);

        placesContainer.removeAllViews();
        for (String place : places) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText("✎  " + place);
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

        List<String> options = new ArrayList<>(places);
        options.add(de.spahr.ausgaben.settings.PlacesStore.NO_PLACE);
        PickerAdapters.places(editDefaultPlace, options);
        String def = placesAccount.isEmpty() ? "" : placesStore.getDefaultPlace(placesAccount);
        editDefaultPlace.setText(def.isEmpty() ? de.spahr.ausgaben.settings.PlacesStore.NO_PLACE : def, false);
    }

    private void renamePlaceDialog(String oldName) {
        android.widget.EditText input = new android.widget.EditText(this);
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

    /**
     * Legt ein neues, leeres Profil an, macht es zum aktiven und öffnet diese Maske dafür – der
     * Nutzer kommt aus {@code SettingsActivity} ("Neues Profil anlegen") oder aus
     * {@link ProfileSwitchDialog} ("+"). Bricht der Nutzer ab, wechselt {@link #abortIfNewProfile()}
     * zurück und entfernt das leere Profil wieder.
     */
    public static void startForNewProfile(android.app.Activity activity) {
        de.spahr.ausgaben.settings.ProfileManager pm =
                new de.spahr.ausgaben.settings.ProfileManager(activity);
        String previousProfileId = pm.getActiveProfileId();
        de.spahr.ausgaben.settings.ProfileManager.Profile newProfile = pm.createProfile(
                activity.getString(R.string.profile_default_name, pm.getProfiles().size() + 1));
        pm.switchTo(activity, newProfile.id);
        Intent intent = new Intent(activity, OnboardingActivity.class);
        intent.putExtra(EXTRA_PREVIOUS_PROFILE_ID, previousProfileId);
        intent.putExtra(EXTRA_NEW_PROFILE_ID, newProfile.id);
        activity.startActivity(intent);
    }

    /**
     * Öffnet diese Maske zum Bearbeiten eines (ggf. noch nicht aktiven) Profils – schaltet bei Bedarf
     * erst dorthin um. Aufrufer: {@code SettingsActivity} ("Profil ändern") und der lange Druck auf
     * eine Zeile in {@link ProfileSwitchDialog}.
     */
    public static void startForEditing(android.app.Activity activity, String profileId) {
        de.spahr.ausgaben.settings.ProfileManager pm =
                new de.spahr.ausgaben.settings.ProfileManager(activity);
        if (!profileId.equals(pm.getActiveProfileId())) {
            pm.switchTo(activity, profileId);
        }
        Intent intent = new Intent(activity, OnboardingActivity.class);
        intent.putExtra(EXTRA_EDITING, true);
        activity.startActivity(intent);
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

    // ---- Verbindung testen / .kmy auswählen (gleiches Verhalten wie in den Einstellungen) ----

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
     * rohen Statuscode – beim Erststart die schnellste Antwort auf „warum geht es nicht?".
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

    private void browseKmy() {
        browseKmyAt(RemotePath.folderOf(textOf(editKmyPath)));
    }

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
     * Navigierbarer Ordner-Dialog (nur Ordner) für die CSV-Export-/Import-Ordner. Nutzt – wie der
     * kmy-Browser – {@link RemoteStorage} mit den aktuell eingegebenen Zugangsdaten, gilt also für
     * Nextcloud, WebDAV und SMB.
     */
    private void browseFolderInto(TextInputEditText target) {
        browseFolderAt(textOf(target), target);
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

    // ---- Konten importieren (gleicher Ablauf wie MainActivity.onAddAccountClicked) ----

    private void importAccounts() {
        saveSettings();
        if (!settings.isKmyMode()) {
            startCsvImport();
            return;
        }
        if (!settings.hasRemoteConfig()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        String path = settings.getKmyPath();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }
        showImportStatus(getString(R.string.progress_download));
        new Thread(() -> {
            try {
                byte[] raw = RemoteStorage.from(settings).downloadBytes(RemotePath.folderOf(path), RemotePath.fileOf(path));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, getApplicationContext()), getApplicationContext());
                runOnUiThread(() -> {
                    hideImportStatus();
                    List<String> accounts = importer.accountNames();
                    List<String> depots = importer.depotNames();
                    if (accounts.isEmpty() && depots.isEmpty()) {
                        Toast.makeText(this, R.string.kmy_no_files, Toast.LENGTH_LONG).show();
                    } else {
                        chooseAccountForImport(importer, accounts, depots);
                    }
                });
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    /** Derselbe Mehrfachauswahl-Dialog wie in MainActivity (ohne Filterung – DB ist beim Start leer). */
    private void chooseAccountForImport(KmyImporter importer, List<String> accounts, List<String> depots) {
        final List<String> accountList = new ArrayList<>(accounts);
        final List<String> depotList = new ArrayList<>(depots);
        List<String> labels = new ArrayList<>(accountList);
        for (String d : depotList) {
            labels.add(getString(R.string.kmy_choose_depot, d));
        }
        final int accountCount = accountList.size();
        final boolean[] checked = new boolean[labels.size()];
        String[] items = labels.toArray(new String[0]);
        new AppDialog(this)
                .setTitle(R.string.kmy_choose_account)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.kmy_import_selected, (d, w) -> {
                    List<String> accountTargets = new ArrayList<>();
                    List<String> depotTargets = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (!checked[i]) {
                            continue;
                        }
                        if (i < accountCount) {
                            accountTargets.add(accountList.get(i));
                        } else {
                            depotTargets.add(depotList.get(i - accountCount));
                        }
                    }
                    if (accountTargets.isEmpty() && depotTargets.isEmpty()) {
                        return;
                    }
                    startBatchImport(importer, accountTargets, depotTargets);
                })
                .show();
    }

    private void startBatchImport(KmyImporter importer, List<String> accountTargets,
                                  List<String> depotTargets) {
        showImportStatus(getString(R.string.onboarding_importing));
        final int importedCount = accountTargets.size() + depotTargets.size();
        new Thread(() -> {
            try {
                if (accountTargets.isEmpty()) {
                    runOnUiThread(() -> importDepotsThenFinish(importer, depotTargets, importedCount));
                    return;
                }
                java.util.LinkedHashMap<String, List<Booking>> map =
                        importer.bookingsForAccounts(accountTargets, null);
                for (String acc : accountTargets) {
                    repository.setAccountCurrency(acc, importer.currencyOf(acc));
                }
                repository.applyAccountTypes(importer.accountTypes());
                repository.applyCategoryTypes(importer.categoryTypes());
                runOnUiThread(() -> repository.replaceImportAccounts(map, null,
                        res -> importDepotsThenFinish(importer, depotTargets, importedCount)));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    private void importDepotsThenFinish(KmyImporter importer, List<String> depots, int importedCount) {
        if (depots.isEmpty()) {
            finishImport(importedCount);
            return;
        }
        final String depot = depots.get(0);
        final List<String> rest = new ArrayList<>(depots.subList(1, depots.size()));
        new Thread(() -> {
            try {
                KmyImporter.DepotData data = importer.importDepot(depot);
                repository.replaceDepotImport(depot, data.securities, data.transactions, data.prices, () ->
                        importDepotsThenFinish(importer, rest, importedCount));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    private void finishImport(int importedCount) {
        importRunning = false;
        importProgress.setVisibility(View.GONE);
        importStatus.setVisibility(View.VISIBLE);
        importStatusText.setText(getString(R.string.onboarding_import_done, importedCount));
        refreshAccountDependentFields();
    }

    /**
     * Nach einem Kontenimport sind die Dropdowns für Standardkonto und die Konto-Auswahl der
     * Orte-Verwaltung noch auf dem alten (meist leeren) Stand – ohne Auffrischen stünden die gerade
     * importierten Konten dort nicht zur Auswahl.
     */
    private void refreshAccountDependentFields() {
        repository.getAccountNames(names -> {
            PickerAdapters.accounts(repository, editDefaultAccount, names);
            PickerAdapters.accounts(repository, editPlacesAccount, names);
            if (placesAccount.isEmpty() && !names.isEmpty()) {
                placesAccount = names.get(0);
                editPlacesAccount.setText(placesAccount, false);
                refreshPlaces();
            }
        });
    }

    // ---- CSV-Import (lokaler Picker; bei Remote-Konfig Ordner durchsuchen) ----

    private void startCsvImport() {
        if (settings.hasRemoteConfig()) {
            browseCsvAt(settings.getImportFolder());
        } else {
            csvLauncher.launch(new String[]{
                    "text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"});
        }
    }

    private void browseCsvAt(String folder) {
        Toast.makeText(this, R.string.loading_files, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                RemoteStorage storage = RemoteStorage.from(settings);
                List<String> folders = storage.listFolders(folder);
                List<String> files = storage.listFiles(folder, "csv");
                java.util.Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);
                java.util.Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
                runOnUiThread(() -> {
                    if (folder.isEmpty() && folders.isEmpty() && files.isEmpty()) {
                        Toast.makeText(this, R.string.no_files, Toast.LENGTH_LONG).show();
                    } else {
                        showCsvPick(folder, folders, files);
                    }
                });
            } catch (Exception e) {
                final String msg = serverError(e);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showCsvPick(String folder, List<String> folders, List<String> files) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (!folder.isEmpty()) {
            labels.add("↑  ..");
            actions.add(() -> browseCsvAt(RemotePath.parentFolder(folder)));
        }
        for (String d : folders) {
            labels.add("📁  " + d);
            final String target = folder.isEmpty() ? d : folder + "/" + d;
            actions.add(() -> browseCsvAt(target));
        }
        for (String f : files) {
            labels.add(f);
            actions.add(() -> downloadAndImportCsv(folder, f));
        }
        String title = folder.isEmpty() ? getString(R.string.choose_import_file) : "/" + folder;
        new AppDialog(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void downloadAndImportCsv(String folder, String fileName) {
        showImportStatus(getString(R.string.onboarding_importing));
        new Thread(() -> {
            try {
                String content = RemoteStorage.from(settings).downloadText(folder, fileName);
                processCsv(content);
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    private void importCsvLocal(Uri uri) {
        showImportStatus(getString(R.string.onboarding_importing));
        new Thread(() -> {
            try {
                processCsv(readText(uri));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    /** Parst den CSV-Inhalt und ersetzt die Buchungen des Kontos (Aufruf aus Hintergrund-Thread). */
    private void processCsv(String content) {
        try {
            CsvImporter importer = new CsvImporter(this);
            List<Booking> bookings = importer.parse(content);
            String account = importer.getParsedAccount();
            runOnUiThread(() -> repository.replaceImport(account, bookings, count -> finishImport(1)));
        } catch (Exception e) {
            postImportError(e);
        }
    }

    // ---- Hilfen ----

    private void showImportStatus(String text) {
        importRunning = true;
        importStatus.setVisibility(View.VISIBLE);
        importProgress.setVisibility(View.VISIBLE);
        importStatusText.setText(text);
    }

    private void hideImportStatus() {
        importRunning = false;
        importStatus.setVisibility(View.GONE);
    }

    private void postImportError(Exception e) {
        final String msg = serverError(e);
        runOnUiThread(() -> {
            hideImportStatus();
            Toast.makeText(this, getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show();
        });
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (is != null && (n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    // ---- Sicherung/Wiederherstellen (nur das aktive Profil) ----

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

    /** Vor dem Sichern fragen: Server-Passwort mitsichern? Datei mit eigenem Passwort verschlüsseln? */
    private void askBackupOptions() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_backup_options, null, false);
        ((TextView) view.findViewById(R.id.backupOptionsMessage))
                .setText(R.string.backup_options_message_profile);
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
                    backupLauncher.launch("ausgaben-profil-" + timestamp() + (p1.isEmpty() ? ".zip" : ".abk"));
                }));
        dialog.show();
    }

    private void doBackup(Uri uri) {
        new Thread(() -> {
            try {
                byte[] file = BackupStore.createProfile(this, backupIncludeServerPassword, backupPassword);
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

    private void confirmRestore() {
        restoreBackupLauncher.launch(new String[]{"*/*"});
    }

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

    /**
     * Eine Profil-Sicherung geht direkt in die Daten/Einstellungen-Auswahl; eine Alle-Profile-Sicherung
     * fragt zuerst, welches der darin enthaltenen Profile das aktive ersetzen soll (siehe
     * {@link BackupStore#restoreProfileFromAllBackup}).
     */
    private void openRestore(byte[] zip) throws Exception {
        final BackupArchive.Content content = BackupArchive.read(zip);
        if (!content.hasData() && !content.hasSettings()) {
            runOnUiThread(() -> Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show());
            return;
        }
        if (content.isAllProfiles()) {
            runOnUiThread(() -> pickProfileFromBackup(content));
        } else {
            runOnUiThread(() -> chooseRestoreScope(content));
        }
    }

    private void pickProfileFromBackup(BackupArchive.Content content) {
        List<String[]> profiles;
        try {
            profiles = BackupStore.profilesInBackup(content);
        } catch (JSONException e) {
            Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i)[1];
        }
        new AppDialog(this)
                .setTitle(R.string.restore_pick_profile_title)
                .setItems(labels, (d, w) -> confirmRestoreFromAllBackup(content, profiles.get(w)[0]))
                .show();
    }

    private void confirmRestoreFromAllBackup(BackupArchive.Content content, String sourceProfileId) {
        new AppDialog(this)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.restore_db, (d, w) -> new Thread(() -> {
                    try {
                        BackupStore.restoreProfileFromAllBackup(this, content, sourceProfileId);
                        postRestoreDone();
                    } catch (Exception e) {
                        postRestoreError(e);
                    }
                }).start())
                .show();
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
                    BackupStore.restoreProfileData(this, content.db);
                }
                if (scope == 1 || scope == 2) {
                    BackupStore.restoreProfileSettings(this, content);
                }
                postRestoreDone();
            } catch (Exception e) {
                postRestoreError(e);
            }
        }).start();
    }

    /** Nach einer Wiederherstellung ist der Activity-Stack auf eine frische MainActivity zu setzen –
     *  dieselbe Begründung wie in {@link #finishFromProfileMask()}: die Datenbank wurde ersetzt. */
    private void postRestoreDone() {
        runOnUiThread(() -> {
            Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show();
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    private void postRestoreError(Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        runOnUiThread(() -> Toast.makeText(this,
                getString(R.string.restore_failed, msg), Toast.LENGTH_LONG).show());
    }

    // ---- Konto löschen/schließen (nur das aktive Profil, siehe Repository) ----

    /**
     * Alle Konten <b>und Depots</b> mit Status als Mehrfachauswahl (Depots tragen den Zusatz „(Depot)").
     * Untere Zeile (vor „Abbrechen"): „Löschen" (immer bei Auswahl) und die kontextabhängige Aktion
     * „Schließen"/„Öffnen" – die gibt es nur für gewöhnliche Konten, nur wenn <b>alle</b> ausgewählten
     * Saldo 0 haben bzw. alle geschlossen sind; sobald ein Depot dabei ist, entfällt sie (Depotsaldo
     * kommt aus Kursen, nicht aus der Buchungssumme).
     */
    private void manageAccounts() {
        repository.getAllAccountsWithStatus(all -> {
            if (all.isEmpty()) {
                Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
                return;
            }
            repository.getAllAccountBalances(balances -> showAccountsDialog(all, balances));
        });
    }

    /** Mehrfachauswahl-Dialog; die Aktionsbuttons werden je nach Auswahl dynamisch ein-/ausgeblendet. */
    private void showAccountsDialog(List<de.spahr.ausgaben.db.Account> accounts, Map<String, Long> balances) {
        String[] items = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            de.spahr.ausgaben.db.Account a = accounts.get(i);
            String status = getString(a.closed
                    ? R.string.account_status_closed : R.string.account_status_active);
            String name = a.isDepot() ? getString(R.string.account_status_depot_marker, a.name) : a.name;
            items[i] = getString(R.string.account_status_line, name, status);
        }
        final boolean[] checked = new boolean[accounts.size()];
        final Runnable[] updater = new Runnable[1];
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
                boolean anyDepot = false;
                boolean allClosed = !sel.isEmpty();
                boolean allOpenZero = !sel.isEmpty();
                for (de.spahr.ausgaben.db.Account a : sel) {
                    if (a.isDepot()) {
                        anyDepot = true;
                    }
                    long bal = balances.containsKey(a.name) ? balances.get(a.name) : 0L;
                    if (!a.closed) {
                        allClosed = false;
                    }
                    if (a.closed || bal != 0) {
                        allOpenZero = false;
                    }
                }
                if (anyDepot) {
                    actBtn.setVisibility(View.GONE);
                } else if (allClosed) {
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
                confirmDeleteAccounts(sel);
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

    private void confirmDeleteAccounts(List<de.spahr.ausgaben.db.Account> selected) {
        List<String> accounts = new ArrayList<>();
        List<String> depots = new ArrayList<>();
        for (de.spahr.ausgaben.db.Account a : selected) {
            (a.isDepot() ? depots : accounts).add(a.name);
        }
        int total = accounts.size() + depots.size();
        String message = depots.isEmpty()
                ? getString(R.string.delete_accounts_confirm_message, total)
                : getString(R.string.delete_accounts_confirm_message_with_depots, total, depots.size());
        AppDialog.destructive(this)
                .setTitle(R.string.delete_account_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteAccountsAndDepots(
                        accounts, depots, () -> {
                    for (String account : accounts) {
                        placesStore.removeAccount(account);
                    }
                    Toast.makeText(this, getString(R.string.accounts_deleted_done, total),
                            Toast.LENGTH_LONG).show();
                    refreshAccountDependentFields();
                }))
                .show();
    }

    // ---- Nur dieses Profil zurücksetzen ----

    /**
     * Setzt nur das aktive Profil zurück (Datenbank + dessen Server-/kmy-Einstellungen); andere Profile
     * bleiben unberührt.
     */
    private void confirmResetProfile() {
        AppDialog.destructive(this)
                .setTitle(R.string.reset_profile_confirm_title)
                .setMessage(R.string.reset_profile_confirm_message)
                .setPositiveButton(R.string.reset_profile_db,
                        (d, w) -> repository.resetAllData(this::finishResetProfile))
                .show();
    }

    private void finishResetProfile() {
        profiles.clearActiveProfileSettings(this);
        Toast.makeText(this, R.string.reset_done, Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

}
