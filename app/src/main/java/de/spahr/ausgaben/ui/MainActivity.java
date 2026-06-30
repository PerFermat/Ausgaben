package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.CsvImporter;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.net.NextcloudUploader;
import de.spahr.ausgaben.settings.SettingsStore;

public class MainActivity extends AppCompatActivity {

    private Repository repository;
    private SettingsStore settings;

    private BookingAdapter adapter;
    private TextView textBalance;

    private List<Booking> allBookings = new ArrayList<>();
    private String filterPayee = "";
    private String filterAccount = "";
    private Long filterAmountCents = null;

    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        repository = new Repository(this);
        settings = new SettingsStore(this);

        textBalance = findViewById(R.id.textBalance);

        RecyclerView recycler = findViewById(R.id.recyclerBookings);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingAdapter();
        adapter.setListener(b -> {
            Intent i = new Intent(MainActivity.this, BookingEditActivity.class);
            i.putExtra(BookingEditActivity.EXTRA_BOOKING_ID, b.id);
            startActivity(i);
        });
        recycler.setAdapter(adapter);

        ExtendedFloatingActionButton fab = findViewById(R.id.fabNew);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, BookingEditActivity.class)));

        exportTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        settings.setLocalExportTree(uri.toString());
                        runExport();
                    }
                });
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        doImportLocal(uri);
                    }
                });

        FloatingActionButton fabScrollTop = findViewById(R.id.fabScrollTop);
        fabScrollTop.setOnClickListener(v -> recycler.smoothScrollToPosition(0));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                // Sichtbar, sobald die Liste nach unten gescrollt wurde.
                if (rv.canScrollVertically(-1)) {
                    fabScrollTop.show();
                } else {
                    fabScrollTop.hide();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBookings();
    }

    private void refreshBookings() {
        repository.getAllBookings(result -> {
            allBookings = result;
            applyFilter();
        });
    }

    // ---- Filter ----

    private void applyFilter() {
        List<Booking> filtered = new ArrayList<>();
        long balance = 0;
        for (Booking b : allBookings) {
            if (!filterPayee.isEmpty()
                    && !b.payee.toLowerCase(Locale.GERMANY).contains(filterPayee.toLowerCase(Locale.GERMANY))) {
                continue;
            }
            if (!filterAccount.isEmpty() && !b.account.equalsIgnoreCase(filterAccount)) {
                continue;
            }
            if (filterAmountCents != null && b.amountCents != filterAmountCents) {
                continue;
            }
            filtered.add(b);
            balance += b.isIncome ? b.amountCents : -b.amountCents;
        }
        adapter.setItems(filtered);
        updateBalance(balance);

        boolean active = !filterPayee.isEmpty() || !filterAccount.isEmpty() || filterAmountCents != null;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(active
                    ? getString(R.string.filter_active, filtered.size()) : null);
        }
    }

    private void updateBalance(long signedCents) {
        textBalance.setText(getString(R.string.balance, formatEuro(signedCents)));
        int color = signedCents < 0
                ? getColor(R.color.expense_red) : getColor(R.color.income_green);
        textBalance.setTextColor(color);
    }

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " €";
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null, false);
        MaterialAutoCompleteTextView fPayee = view.findViewById(R.id.filterPayee);
        MaterialAutoCompleteTextView fAccount = view.findViewById(R.id.filterAccount);
        TextInputEditText fAmount = view.findViewById(R.id.filterAmount);
        fAmount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        repository.getPayeeNames(names -> fPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> fAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        fPayee.setText(filterPayee);
        fAccount.setText(filterAccount, false);
        if (filterAmountCents != null) {
            fAmount.setText(formatCents(filterAmountCents));
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.filter_title)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (d, w) -> {
                    filterPayee = textOf(fPayee).trim();
                    filterAccount = textOf(fAccount).trim();
                    filterAmountCents = parseAmountToCents(textOf(fAmount));
                    applyFilter();
                })
                .setNeutralButton(R.string.filter_reset, (d, w) -> {
                    filterPayee = "";
                    filterAccount = "";
                    filterAmountCents = null;
                    applyFilter();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String formatCents(long cents) {
        long euros = cents / 100;
        long rest = Math.abs(cents % 100);
        return euros + "," + String.format(Locale.GERMANY, "%02d", rest);
    }

    private Long parseAmountToCents(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(" ", "").replace(",", ".");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(normalized).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private String textOf(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    // ---- Menü / Aktionen ----

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import) {
            onImportClicked();
            return true;
        } else if (id == R.id.action_export) {
            doExport();
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        } else if (id == R.id.action_analysis) {
            Intent i = new Intent(this, AnalysisActivity.class);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_PAYEE, filterPayee);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_ACCOUNT, filterAccount);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_AMOUNT,
                    filterAmountCents == null ? -1L : filterAmountCents);
            startActivity(i);
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doExport() {
        // Ohne Nextcloud-Config lokal exportieren; ggf. zuerst Zielordner wählen.
        if (!settings.hasNextcloudConfig() && settings.getLocalExportTree().isEmpty()) {
            Toast.makeText(this, R.string.choose_export_folder, Toast.LENGTH_LONG).show();
            exportTreeLauncher.launch(null);
            return;
        }
        runExport();
    }

    private void runExport() {
        Toast.makeText(this, R.string.export_running, Toast.LENGTH_SHORT).show();
        String tree = settings.hasNextcloudConfig() ? null : settings.getLocalExportTree();
        new ExportCoordinator(this, repository, settings, tree).exportUnexported((message, refreshNeeded) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (refreshNeeded) {
                refreshBookings();
            }
        });
    }

    // ---- Import (Nextcloud-Liste oder lokaler Picker) ----

    private void onImportClicked() {
        if (settings.hasNextcloudConfig()) {
            loadNextcloudFileList();
        } else {
            importLauncher.launch(new String[]{
                    "text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"});
        }
    }

    private void loadNextcloudFileList() {
        Toast.makeText(this, R.string.loading_files, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> files = new NextcloudUploader().listCsvFiles(
                        settings.getUrl(), settings.getUser(), settings.getPassword(),
                        settings.getImportFolder());
                runOnUiThread(() -> {
                    if (files.isEmpty()) {
                        Toast.makeText(this, R.string.no_files, Toast.LENGTH_LONG).show();
                    } else {
                        showFilePickDialog(files);
                    }
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showFilePickDialog(List<String> files) {
        String[] items = files.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.choose_import_file)
                .setItems(items, (d, which) -> downloadAndImport(items[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadAndImport(String fileName) {
        new Thread(() -> {
            try {
                String content = new NextcloudUploader().downloadText(
                        settings.getUrl(), settings.getUser(), settings.getPassword(),
                        settings.getImportFolder(), fileName);
                processImport(content);
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void doImportLocal(Uri uri) {
        new Thread(() -> {
            try {
                processImport(readText(uri));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Parst den Inhalt und ersetzt die exportierten Buchungen des Kontos. Aufruf aus Hintergrund-Thread. */
    private void processImport(String content) {
        try {
            CsvImporter importer = new CsvImporter();
            List<Booking> bookings = importer.parse(content);
            String account = importer.getParsedAccount();
            runOnUiThread(() -> repository.replaceImport(account, bookings, count -> {
                Toast.makeText(this, getString(R.string.import_done, count), Toast.LENGTH_LONG).show();
                refreshBookings();
            }));
        } catch (Exception e) {
            final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show());
        }
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
}
