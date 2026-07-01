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
import de.spahr.ausgaben.db.PlaceBalance;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.CsvImporter;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.export.KmyDocument;
import de.spahr.ausgaben.export.KmyExportCoordinator;
import de.spahr.ausgaben.export.KmyImporter;
import de.spahr.ausgaben.net.NextcloudUploader;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

public class MainActivity extends AppCompatActivity {

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;

    private BookingAdapter adapter;
    private TextView textBalance;
    private TextView textSaldoLabel;

    private List<Booking> allBookings = new ArrayList<>();
    private java.util.Map<String, Long> placeBalances = new java.util.LinkedHashMap<>();
    private long totalBalance = 0;
    private long allPlaceEntrySum = 0;
    private long filteredSum = 0;
    private final List<SaldoView> saldoViews = new ArrayList<>();
    private int saldoIndex = 0;

    private String filterPayee = "";
    private String filterAccount = "";
    private String filterCategory = "";
    private boolean filterCategoryIsMain = false;
    private Long filterAmountFrom = null;
    private Long filterAmountTo = null;

    private List<String> allCategories = new ArrayList<>();

    /** Eine Sicht in der Saldo-Leiste. key: TOTAL | PLACE:<name> | NOPLACE | FILTERED */
    private static final class SaldoView {
        final String key;
        final String label;
        final long cents;

        SaldoView(String key, String label, long cents) {
            this.key = key;
            this.label = label;
            this.cents = cents;
        }
    }

    public static final String VIEW_TOTAL = "TOTAL";
    public static final String VIEW_NOPLACE = "NOPLACE";
    public static final String VIEW_FILTERED = "FILTERED";
    public static final String VIEW_PLACE_PREFIX = "PLACE:";

    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        repository = new Repository(this);
        settings = new SettingsStore(this);
        placesStore = new PlacesStore(this);

        textBalance = findViewById(R.id.textBalance);
        textSaldoLabel = findViewById(R.id.textSaldoLabel);
        findViewById(R.id.saldoHeader).setOnClickListener(v -> cycleSaldo());

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
        repository.getCategoryNames(cats -> allCategories = cats);
        repository.getAllBookings(result -> {
            allBookings = result;
            repository.getPlaceBalances(pb -> {
                placeBalances = new java.util.LinkedHashMap<>();
                allPlaceEntrySum = 0;
                for (PlaceBalance b : pb) {
                    placeBalances.put(b.place, b.balanceCents);
                    allPlaceEntrySum += b.balanceCents;
                }
                applyFilter();
            });
        });
    }

    // ---- Filter ----

    private void applyFilter() {
        List<Booking> filtered = new ArrayList<>();
        filteredSum = 0;
        totalBalance = 0;
        for (Booking b : allBookings) {
            totalBalance += b.isIncome ? b.amountCents : -b.amountCents;
            if (matchesFilter(b)) {
                filtered.add(b);
                filteredSum += b.isIncome ? b.amountCents : -b.amountCents;
            }
        }
        adapter.setItems(filtered);
        buildSaldoViews();
        showSaldo();

        boolean active = isFilterActive();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(active
                    ? getString(R.string.filter_active, filtered.size()) : null);
        }
    }

    private boolean matchesFilter(Booking b) {
        if (!filterPayee.isEmpty()
                && !b.payee.toLowerCase(Locale.GERMANY).contains(filterPayee.toLowerCase(Locale.GERMANY))) {
            return false;
        }
        if (!filterAccount.isEmpty() && !b.account.equalsIgnoreCase(filterAccount)) {
            return false;
        }
        if (!filterCategory.isEmpty() && !categoryMatches(b.category)) {
            return false;
        }
        if (filterAmountFrom != null && b.amountCents < filterAmountFrom) {
            return false;
        }
        return filterAmountTo == null || b.amountCents <= filterAmountTo;
    }

    private boolean categoryMatches(String cat) {
        if (cat == null) {
            cat = "";
        }
        if (filterCategoryIsMain) {
            // Hauptkategorie: exakt oder als Präfix inkl. Unterkategorien.
            return cat.equalsIgnoreCase(filterCategory)
                    || cat.toLowerCase(Locale.GERMANY).startsWith(
                            filterCategory.toLowerCase(Locale.GERMANY) + ":");
        }
        return cat.equalsIgnoreCase(filterCategory);
    }

    private boolean isFilterActive() {
        return !filterPayee.isEmpty() || !filterAccount.isEmpty() || !filterCategory.isEmpty()
                || filterAmountFrom != null || filterAmountTo != null;
    }

    // ---- Saldo-Leiste (Durchschalten) ----

    private void buildSaldoViews() {
        saldoViews.clear();
        String accountLabel = settings.getDefaultAccount();
        if (accountLabel.isEmpty()) {
            accountLabel = getString(R.string.saldo_total);
        }
        saldoViews.add(new SaldoView(VIEW_TOTAL, accountLabel, totalBalance));
        // Orte mit Saldo 0 werden beim Durchschalten übersprungen (nicht aufgenommen).
        for (String place : placesStore.getPlaces()) {
            long bal = placeBalances.containsKey(place) ? placeBalances.get(place) : 0L;
            if (bal != 0) {
                saldoViews.add(new SaldoView(VIEW_PLACE_PREFIX + place, place, bal));
            }
        }
        long ohneOrt = totalBalance - allPlaceEntrySum;
        if (ohneOrt != 0) {
            saldoViews.add(new SaldoView(VIEW_NOPLACE, getString(R.string.no_place), ohneOrt));
        }
        if (isFilterActive()) {
            saldoViews.add(new SaldoView(VIEW_FILTERED, getString(R.string.saldo_filtered), filteredSum));
        }
        if (saldoIndex >= saldoViews.size()) {
            saldoIndex = 0;
        }
    }

    private void showSaldo() {
        if (saldoViews.isEmpty()) {
            return;
        }
        SaldoView v = saldoViews.get(saldoIndex % saldoViews.size());
        textSaldoLabel.setText(v.label);
        textBalance.setText(getString(R.string.balance, formatEuro(v.cents)));
        textBalance.setTextColor(v.cents < 0 ? getColor(R.color.expense_red) : getColor(R.color.income_green));
    }

    private void cycleSaldo() {
        if (saldoViews.isEmpty()) {
            return;
        }
        saldoIndex = (saldoIndex + 1) % saldoViews.size();
        showSaldo();
        flashSaldoBar();
    }

    /** Kurzes Aufhellen der (dauerhaft grauen) Saldo-Leiste beim Wechsel. */
    private void flashSaldoBar() {
        final View bar = findViewById(R.id.saldoHeader);
        int from = getColor(R.color.saldo_bar_flash);
        int to = getColor(R.color.saldo_bar_bg);
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofObject(
                new android.animation.ArgbEvaluator(), from, to);
        anim.setDuration(350);
        anim.addUpdateListener(a -> bar.setBackgroundColor((int) a.getAnimatedValue()));
        anim.start();
    }

    private int indexOfFilteredView() {
        for (int i = 0; i < saldoViews.size(); i++) {
            if (VIEW_FILTERED.equals(saldoViews.get(i).key)) {
                return i;
            }
        }
        return 0;
    }

    private String currentViewKey() {
        if (saldoViews.isEmpty()) {
            return VIEW_TOTAL;
        }
        return saldoViews.get(saldoIndex % saldoViews.size()).key;
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
        MaterialAutoCompleteTextView fCategory = view.findViewById(R.id.filterCategory);
        com.google.android.material.slider.RangeSlider slider = view.findViewById(R.id.filterAmountSlider);
        TextInputEditText fFrom = view.findViewById(R.id.filterAmountFrom);
        TextInputEditText fTo = view.findViewById(R.id.filterAmountTo);
        fFrom.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        fTo.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        repository.getPayeeNames(names -> fPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> fAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        fPayee.setText(filterPayee);
        fAccount.setText(filterAccount, false);

        // Kategorie-Baum
        final String[] catValue = {filterCategory};
        final boolean[] catIsMain = {filterCategoryIsMain};
        CategoryFilterAdapter catAdapter =
                new CategoryFilterAdapter(this, getString(R.string.category_all), allCategories);
        fCategory.setAdapter(catAdapter);
        fCategory.setText(filterCategory, false);
        fCategory.setOnItemClickListener((parent, v, pos, id) -> {
            CategoryFilterAdapter.CatItem it = catAdapter.getItem(pos);
            if (it != null) {
                catValue[0] = it.value;
                catIsMain[0] = it.isMain;
                fCategory.setText(it.value, false);
            }
        });

        // Betrag-Range
        long dMin = Long.MAX_VALUE;
        long dMax = Long.MIN_VALUE;
        for (Booking b : allBookings) {
            if (b.amountCents < dMin) dMin = b.amountCents;
            if (b.amountCents > dMax) dMax = b.amountCents;
        }
        final long dataMin = dMin;
        final long dataMax = dMax;
        final boolean hasRange = !allBookings.isEmpty() && dataMax > dataMin;
        if (hasRange) {
            float minE = dataMin / 100f;
            float maxE = dataMax / 100f;
            slider.setValueFrom(minE);
            slider.setValueTo(maxE);
            float curFrom = filterAmountFrom != null ? filterAmountFrom / 100f : minE;
            float curTo = filterAmountTo != null ? filterAmountTo / 100f : maxE;
            curFrom = Math.max(minE, Math.min(maxE, curFrom));
            curTo = Math.max(minE, Math.min(maxE, curTo));
            if (curFrom > curTo) {
                curFrom = minE;
                curTo = maxE;
            }
            slider.setValues(curFrom, curTo);
            slider.setLabelFormatter(value -> formatEuro(Math.round(value * 100)));

            final boolean[] syncing = {false};
            fFrom.setText(formatCents(Math.round(curFrom * 100)));
            fTo.setText(formatCents(Math.round(curTo * 100)));

            slider.addOnChangeListener((s, val, fromUser) -> {
                if (syncing[0]) return;
                syncing[0] = true;
                java.util.List<Float> vals = s.getValues();
                fFrom.setText(formatCents(Math.round(vals.get(0) * 100)));
                fTo.setText(formatCents(Math.round(vals.get(1) * 100)));
                syncing[0] = false;
            });

            android.text.TextWatcher tw = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable e) {
                    if (syncing[0]) return;
                    Long fromC = parseAmountToCents(textOf(fFrom));
                    Long toC = parseAmountToCents(textOf(fTo));
                    if (fromC == null || toC == null) return;
                    float f = Math.max(dataMin, Math.min(dataMax, fromC)) / 100f;
                    float t = Math.max(dataMin, Math.min(dataMax, toC)) / 100f;
                    if (f > t) return;
                    syncing[0] = true;
                    slider.setValues(f, t);
                    syncing[0] = false;
                }
            };
            fFrom.addTextChangedListener(tw);
            fTo.addTextChangedListener(tw);
        } else {
            // Kein sinnvoller Bereich (0/1 Buchung oder alle gleich) → deaktivieren.
            slider.setValueFrom(0f);
            slider.setValueTo(1f);
            slider.setValues(0f, 1f);
            slider.setEnabled(false);
            fFrom.setEnabled(false);
            fTo.setEnabled(false);
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.filter_title)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (d, w) -> {
                    filterPayee = textOf(fPayee).trim();
                    filterAccount = textOf(fAccount).trim();
                    filterCategory = catValue[0] == null ? "" : catValue[0].trim();
                    filterCategoryIsMain = catIsMain[0];
                    if (hasRange) {
                        java.util.List<Float> vals = slider.getValues();
                        long fromC = Math.round(vals.get(0) * 100);
                        long toC = Math.round(vals.get(1) * 100);
                        if (fromC <= dataMin && toC >= dataMax) {
                            filterAmountFrom = null;
                            filterAmountTo = null;
                        } else {
                            filterAmountFrom = fromC;
                            filterAmountTo = toC;
                        }
                    } else {
                        filterAmountFrom = null;
                        filterAmountTo = null;
                    }
                    applyFilter();
                    // Filter angelegt/geändert → automatisch die gefilterte Summe anzeigen.
                    if (isFilterActive()) {
                        saldoIndex = indexOfFilteredView();
                        showSaldo();
                        flashSaldoBar();
                    }
                })
                .setNeutralButton(R.string.filter_reset, (d, w) -> {
                    filterPayee = "";
                    filterAccount = "";
                    filterCategory = "";
                    filterCategoryIsMain = false;
                    filterAmountFrom = null;
                    filterAmountTo = null;
                    saldoIndex = 0;
                    applyFilter();
                    showSaldo();
                    flashSaldoBar();
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
            i.putExtra(AnalysisActivity.EXTRA_FILTER_CATEGORY, filterCategory);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_CATEGORY_MAIN, filterCategoryIsMain);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_AMOUNT_FROM,
                    filterAmountFrom == null ? Long.MIN_VALUE : filterAmountFrom);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_AMOUNT_TO,
                    filterAmountTo == null ? Long.MAX_VALUE : filterAmountTo);
            i.putExtra(AnalysisActivity.EXTRA_VIEW_KEY, currentViewKey());
            startActivity(i);
            return true;
        } else if (id == R.id.action_balance) {
            startActivity(new Intent(this, BalanceActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doExport() {
        if (settings.isKmyMode()) {
            runKmyExport();
            return;
        }
        // Ohne Nextcloud-Config lokal exportieren; ggf. zuerst Zielordner wählen.
        if (!settings.hasNextcloudConfig() && settings.getLocalExportTree().isEmpty()) {
            Toast.makeText(this, R.string.choose_export_folder, Toast.LENGTH_LONG).show();
            exportTreeLauncher.launch(null);
            return;
        }
        runExport();
    }

    private void runKmyExport() {
        showProgress(getString(R.string.progress_exporting));
        new KmyExportCoordinator(this, repository, settings).exportUnexported(
                new KmyExportCoordinator.Listener() {
                    @Override
                    public void onProgress(String stage) {
                        updateProgress(stage);
                    }

                    @Override
                    public void onComplete(String message, boolean refreshNeeded) {
                        dismissProgress();
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        if (refreshNeeded) {
                            refreshBookings();
                        }
                    }
                });
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
        if (settings.isKmyMode()) {
            startKmyImport();
            return;
        }
        if (settings.hasNextcloudConfig()) {
            loadNextcloudFileList();
        } else {
            importLauncher.launch(new String[]{
                    "text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"});
        }
    }

    // ---- KMyMoney-Import (.kmy) ----

    private void startKmyImport() {
        if (!settings.hasNextcloudConfig()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        String path = settings.getKmyPath();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }
        // Immer die in den Einstellungen hinterlegte Datei verwenden (keine Dateiauswahl).
        downloadKmyAndChooseAccount(folderOf(path), fileOf(path));
    }

    private void downloadKmyAndChooseAccount(String folder, String fileName) {
        showProgress(getString(R.string.progress_download));
        new Thread(() -> {
            try {
                byte[] raw = new NextcloudUploader().downloadBytes(settings.getUrl(),
                        settings.getUser(), settings.getPassword(), folder, fileName);
                KmyDocument doc = new KmyDocument(raw);
                KmyImporter importer = new KmyImporter(doc);
                List<String> accounts = importer.accountNames();
                runOnUiThread(() -> {
                    dismissProgress();
                    if (accounts.isEmpty()) {
                        Toast.makeText(this, R.string.kmy_no_files, Toast.LENGTH_LONG).show();
                    } else {
                        chooseAccountForImport(importer, accounts);
                    }
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> {
                    dismissProgress();
                    Toast.makeText(this, getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void chooseAccountForImport(KmyImporter importer, List<String> accounts) {
        String[] items = accounts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.kmy_choose_account)
                .setItems(items, (d, w) -> confirmKmyImport(importer, items[w]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmKmyImport(KmyImporter importer, String account) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.kmy_replace_title)
                .setMessage(getString(R.string.kmy_replace_message, account))
                .setPositiveButton(R.string.kmy_import_replace, (d, w) -> runKmyImport(importer, account))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runKmyImport(KmyImporter importer, String account) {
        showProgress(getString(R.string.progress_importing));
        new Thread(() -> {
            try {
                List<Booking> bookings = importer.bookingsForAccount(account);
                runOnUiThread(() -> repository.replaceImport(account, bookings, count -> {
                    dismissProgress();
                    Toast.makeText(this, getString(R.string.kmy_import_done, count, account),
                            Toast.LENGTH_LONG).show();
                    refreshBookings();
                }));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> {
                    dismissProgress();
                    Toast.makeText(this, getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static String folderOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    private static String fileOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    // ---- Fortschrittsdialog ----

    private androidx.appcompat.app.AlertDialog progressDialog;
    private TextView progressTextView;

    private void showProgress(String text) {
        if (progressDialog != null && progressDialog.isShowing()) {
            updateProgress(text);
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null, false);
        progressTextView = view.findViewById(R.id.progressText);
        progressTextView.setText(text);
        progressDialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setView(view)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void updateProgress(String text) {
        if (progressTextView != null) {
            progressTextView.setText(text);
        }
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
            progressTextView = null;
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
