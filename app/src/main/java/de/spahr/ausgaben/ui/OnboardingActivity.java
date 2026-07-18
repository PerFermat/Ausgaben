package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
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
public class OnboardingActivity extends LocalizedActivity {

    private Repository repository;
    private SettingsStore settings;

    private MaterialAutoCompleteTextView editLanguage;
    private MaterialAutoCompleteTextView editExportMode;
    private MaterialAutoCompleteTextView editServerType;
    private TextInputEditText editUrl;
    private TextInputEditText editUser;
    private TextInputEditText editPassword;
    private TextInputEditText editFolder;
    private TextInputEditText editImportFolder;
    private TextInputEditText editKmyPath;
    private TextInputLayout urlLayout;
    private TextInputLayout userLayout;
    private TextInputLayout passwordLayout;
    private LinearLayout importStatus;
    private View importProgress;
    private TextView importStatusText;

    private List<Language> languages = new ArrayList<>();
    private String selectedExportMode = SettingsStore.MODE_CSV;
    private String selectedServerType = SettingsStore.SERVER_NEXTCLOUD;

    private ActivityResultLauncher<String[]> csvLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        repository = new Repository(this);
        settings = new SettingsStore(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        editLanguage = findViewById(R.id.editLanguage);
        editExportMode = findViewById(R.id.editExportMode);
        editServerType = findViewById(R.id.editServerType);
        editUrl = findViewById(R.id.editUrl);
        editUser = findViewById(R.id.editUser);
        editPassword = findViewById(R.id.editPassword);
        editFolder = findViewById(R.id.editFolder);
        editImportFolder = findViewById(R.id.editImportFolder);
        editKmyPath = findViewById(R.id.editKmyPath);
        urlLayout = findViewById(R.id.urlLayout);
        userLayout = findViewById(R.id.userLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        importStatus = findViewById(R.id.importStatus);
        importProgress = findViewById(R.id.importProgress);
        importStatusText = findViewById(R.id.importStatusText);

        setupLanguages();
        setupExportMode();
        setupServerType();
        prefillSyncFields();

        csvLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        importCsvLocal(uri);
                    }
                });

        ((MaterialButton) findViewById(R.id.btnTestConnection))
                .setOnClickListener(v -> testConnection());
        ((MaterialButton) findViewById(R.id.btnBrowseKmy))
                .setOnClickListener(v -> browseKmy());
        ((MaterialButton) findViewById(R.id.btnImportAccounts))
                .setOnClickListener(v -> importAccounts());
        ((MaterialButton) findViewById(R.id.btnMoreSettings)).setOnClickListener(v -> {
            saveSettings();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        ((MaterialButton) findViewById(R.id.btnDone)).setOnClickListener(v -> {
            saveSettings();
            finish();
        });
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
        AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(code));
    }

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

    private void applyExportModeVisibility() {
        boolean kmy = SettingsStore.MODE_KMY.equals(selectedExportMode);
        findViewById(R.id.csvOptions).setVisibility(kmy ? View.GONE : View.VISIBLE);
        findViewById(R.id.kmyOptions).setVisibility(kmy ? View.VISIBLE : View.GONE);
    }

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

    private void applyServerTypeHints() {
        boolean smb = SettingsStore.SERVER_SMB.equals(selectedServerType);
        urlLayout.setHint(getString(smb ? R.string.smb_url_hint : R.string.nextcloud_url_hint));
        userLayout.setHint(getString(smb ? R.string.smb_user_hint : R.string.nextcloud_user_hint));
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
        settings.save(
                textOf(editUrl),
                textOf(editUser),
                textOf(editPassword),
                textOf(editFolder),
                textOf(editImportFolder),
                settings.getDefaultAccount(),
                selectedExportMode,
                textOf(editKmyPath),
                selectedServerType);
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
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.conn_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void browseKmy() {
        browseKmyAt(folderOf(textOf(editKmyPath)));
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
                RemoteStorage storage = RemoteStorage.from(serverType, url, user, password);
                List<String> folders = storage.listFolders(folder);
                List<String> files = storage.listFiles(folder, "kmy");
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
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
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
            actions.add(() -> browseKmyAt(parentFolder(folder)));
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
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .setNegativeButton(R.string.cancel, null)
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
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path));
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
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
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
                .setNegativeButton(R.string.cancel, null)
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
                repository.replaceDepotImport(depot, data.securities, data.transactions, () ->
                        importDepotsThenFinish(importer, rest, importedCount));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    private void finishImport(int importedCount) {
        importProgress.setVisibility(View.GONE);
        importStatus.setVisibility(View.VISIBLE);
        importStatusText.setText(getString(R.string.onboarding_import_done, importedCount));
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
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
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
            actions.add(() -> browseCsvAt(parentFolder(folder)));
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
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .setNegativeButton(R.string.cancel, null)
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
        importStatus.setVisibility(View.VISIBLE);
        importProgress.setVisibility(View.VISIBLE);
        importStatusText.setText(text);
    }

    private void hideImportStatus() {
        importStatus.setVisibility(View.GONE);
    }

    private void postImportError(Exception e) {
        final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
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

    private static String folderOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    private static String parentFolder(String folder) {
        String p = folder == null ? "" : folder.trim();
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    private static String fileOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }
}
