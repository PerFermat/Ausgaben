package de.spahr.ausgaben.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.CsvImporter;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.settings.SettingsStore;

public class SettingsActivity extends AppCompatActivity {

    private SettingsStore settings;
    private Repository repository;

    private TextInputEditText editUrl;
    private TextInputEditText editUser;
    private TextInputEditText editPassword;
    private TextInputLayout passwordLayout;
    private TextInputEditText editFolder;
    private MaterialAutoCompleteTextView editDefaultAccount;
    private MaterialSwitch switchDarkMode;

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> backupLauncher;

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
        passwordLayout = findViewById(R.id.passwordLayout);
        editFolder = findViewById(R.id.editFolder);
        editDefaultAccount = findViewById(R.id.editDefaultAccount);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        editUrl.setText(settings.getUrl());
        editUser.setText(settings.getUser());
        editFolder.setText(settings.getFolder());
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

        repository.getAccountNames(names -> editDefaultAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        registerLaunchers();

        MaterialButton btnSave = findViewById(R.id.btnSaveSettings);
        btnSave.setOnClickListener(v -> save());
        ((MaterialButton) findViewById(R.id.btnImport)).setOnClickListener(
                v -> importLauncher.launch(new String[]{"text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"}));
        ((MaterialButton) findViewById(R.id.btnExportAll)).setOnClickListener(v -> exportAll());
        ((MaterialButton) findViewById(R.id.btnBackup)).setOnClickListener(
                v -> backupLauncher.launch("ausgaben-backup-" + timestamp() + ".db"));
    }

    private void registerLaunchers() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        doImport(uri);
                    }
                });
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        doBackup(uri);
                    }
                });
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
                defaultAccount);

        repository.ensureAccount(defaultAccount);

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void exportAll() {
        Toast.makeText(this, R.string.export_all_running, Toast.LENGTH_SHORT).show();
        new ExportCoordinator(this, repository, settings).exportAll(
                (message, refreshNeeded) -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void doImport(Uri uri) {
        new Thread(() -> {
            try {
                String content = readText(uri);
                List<Booking> bookings = new CsvImporter().parse(content);
                runOnUiThread(() -> repository.importBookings(bookings, count ->
                        Toast.makeText(this, getString(R.string.import_done, count), Toast.LENGTH_LONG).show()));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
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

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY).format(new Date());
    }

    private String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
}
