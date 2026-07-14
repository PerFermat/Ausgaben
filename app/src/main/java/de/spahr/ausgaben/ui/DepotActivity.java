package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.export.KmyDocument;
import de.spahr.ausgaben.export.KmyExportCoordinator;
import de.spahr.ausgaben.export.KmyImporter;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Depot-Ansicht im Konto-Look: Schublade (Hamburger) + Depotname im Kopf, Menü (Export/Filter/Bestände/
 * Einstellungen) und eine per Klick durchschaltbare Saldenzeile (Depotwert → Nettoeinsatz → Gewinn/Verlust)
 * für das komplette Depot. Darunter die Wertpapiere; ein Tipp öffnet die Einzel-Historie.
 */
public class DepotActivity extends LocalizedActivity {

    /** Optional: dieses Depot anzeigen (aus der Kontenschublade). Leer = erstes Depot. */
    public static final String EXTRA_DEPOT = "depot";

    private Repository repository;
    private SettingsStore settings;
    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private AccountDrawerAdapter accountAdapter;
    private LinearLayout container;
    private TextView saldoLabel;
    private TextView saldoValue;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;

    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private AlertDialog progressDialog;
    private TextView progressTextView;

    /** Gelber Import-Banner (Depot-Aktualisierung im Hintergrund), wie im Hauptbildschirm. */
    private View importBanner;
    private ShimmerView importShimmer;
    private TextView importStatus;
    private TextView importPercent;

    private String depot;
    private Repository.DepotMetrics metrics;
    private java.util.List<Integer> saldoModes = new java.util.ArrayList<>();
    private int saldoIndex = 0;

    // Filter (leer = alles): Wertpapiername + Wert von/bis (Cent, null = offen).
    private String filterName = "";
    private Long filterFrom;
    private Long filterTo;
    /** Zuletzt gerenderte Bestände – für die Slider-Grenzen des Wertfilters. */
    private List<Repository.DepotHolding> lastHoldings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depot);
        repository = new Repository(this);
        settings = new SettingsStore(this);

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

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawerLayout = findViewById(R.id.drawerLayout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(getColor(R.color.white));

        container = findViewById(R.id.depotContainer);
        saldoLabel = findViewById(R.id.textSaldoLabel);
        saldoValue = findViewById(R.id.textBalance);
        findViewById(R.id.saldoHeader).setOnClickListener(v -> {
            if (!saldoModes.isEmpty()) {
                saldoIndex = (saldoIndex + 1) % saldoModes.size();
                showSaldo();
            }
        });

        setupDrawer();
        depot = getIntent().getStringExtra(EXTRA_DEPOT);

        // Herunterziehen: das angezeigte Depot neu aus der .kmy einlesen (nur kmy-Modus).
        importBanner = findViewById(R.id.importBanner);
        importShimmer = findViewById(R.id.importShimmer);
        importStatus = findViewById(R.id.importStatus);
        importPercent = findViewById(R.id.importPercent);
        importShimmer.setColors(getColor(R.color.import_banner_bg), getColor(R.color.import_banner_shimmer));
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            swipeRefresh.setRefreshing(false);
            if (depot != null && !depot.isEmpty()) {
                reimportDepot(depot);
            }
        });

        // Scroll-nach-oben-Knopf, sobald nach unten gescrollt wurde.
        android.widget.ScrollView depotScroll = findViewById(R.id.depotScroll);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabScrollTop =
                findViewById(R.id.fabScrollTop);
        fabScrollTop.setOnClickListener(v -> depotScroll.smoothScrollTo(0, 0));
        depotScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (y > 0) {
                fabScrollTop.show();
            } else {
                fabScrollTop.hide();
            }
        });
    }

    private void setupDrawer() {
        RecyclerView list = findViewById(R.id.accountList);
        list.setLayoutManager(new LinearLayoutManager(this));
        accountAdapter = new AccountDrawerAdapter(getString(R.string.account_all),
                new AccountDrawerAdapter.Listener() {
                    @Override
                    public void onSelect(String account, boolean isAll) {
                        drawerLayout.closeDrawers();
                        openMainAccount(isAll ? "" : account);
                    }

                    @Override
                    public void onImport(String account, boolean isAll) {
                        // Import läuft über den Hauptbildschirm.
                        drawerLayout.closeDrawers();
                        openMainAccount(isAll ? "" : account);
                    }

                    @Override
                    public void onDepotSelect(String d) {
                        drawerLayout.closeDrawers();
                        if (!d.equals(depot)) {
                            depot = d;
                            saldoIndex = 0;
                            render();
                        }
                    }

                    @Override
                    public void onDepotImport(String d) {
                        // Langer Druck auf das Depot: dieses Depot neu einlesen (Schublade bleibt offen).
                        reimportDepot(d);
                    }
                });
        list.setAdapter(accountAdapter);
        findViewById(R.id.addAccount).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            openMainAccount("");
        });
        repository.getAccountsGrouped(g -> accountAdapter.setAccounts(g.assets, g.liabilities));
        repository.getDepots(accountAdapter::setDepots);
    }

    private void setTitleText(String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        } else {
            toolbar.setTitle(title);
        }
    }

    /** Wechselt zum Hauptbildschirm und wählt dort ein Konto (leer = „Alle Konten"). */
    private void openMainAccount(String account) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(MainActivity.EXTRA_SELECT_ACCOUNT, account);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (depot == null || depot.isEmpty()) {
            repository.getDepots(depots -> {
                if (!depots.isEmpty()) {
                    depot = depots.get(0);
                    render();
                } else {
                    setTitleText(getString(R.string.depot_title));
                    container.removeAllViews();
                    TextView empty = new TextView(this);
                    empty.setText(R.string.depot_empty);
                    empty.setPadding(0, 24, 0, 0);
                    container.addView(empty);
                }
            });
            return;
        }
        setTitleText(depot);
        repository.getDepotMetrics(depot, m -> {
            metrics = m;
            saldoModes = DepotSaldo.modes(m);
            if (saldoIndex >= saldoModes.size()) {
                saldoIndex = 0;
            }
            showSaldo();
        });
        repository.getDepotHoldings(depot, this::renderHoldings);
    }

    private void renderHoldings(List<Repository.DepotHolding> holdings) {
        lastHoldings = holdings;
        container.removeAllViews();
        boolean any = false;
        for (Repository.DepotHolding h : holdings) {
            if (Math.abs(h.shares) < 1e-6 || !matchesFilter(h)) {
                continue;
            }
            any = true;
            String left = h.name + (h.symbol.isEmpty() ? "" : "  ·  " + h.symbol)
                    + "\n" + shares(h.shares) + " × " + price(h.price);
            addRow(left, money(h.valueCents), v -> openHistory(h));
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText(isFilterActive() ? R.string.depot_no_match : R.string.depot_empty);
            empty.setPadding(0, 24, 0, 0);
            container.addView(empty);
        }
    }

    private boolean matchesFilter(Repository.DepotHolding h) {
        if (!filterName.isEmpty()
                && !h.name.toLowerCase(Locale.getDefault()).contains(filterName.toLowerCase(Locale.getDefault()))) {
            return false;
        }
        if (filterFrom != null && h.valueCents < filterFrom) {
            return false;
        }
        return filterTo == null || h.valueCents <= filterTo;
    }

    private boolean isFilterActive() {
        return !filterName.isEmpty() || filterFrom != null || filterTo != null;
    }

    // ---- Saldenzeile ----

    private void showSaldo() {
        if (metrics == null || saldoModes.isEmpty()) {
            return;
        }
        int mode = saldoModes.get(saldoIndex % saldoModes.size());
        DepotSaldo.apply(this, saldoLabel, saldoValue, metrics, mode,
                getString(R.string.depot_value_label));
    }

    // ---- Menü ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.depot_menu, menu);
        setMenuTitle(menu, R.id.action_export, R.string.action_export);
        setMenuTitle(menu, R.id.action_analysis, R.string.action_analysis);
        setMenuTitle(menu, R.id.action_filter, R.string.action_filter);
        setMenuTitle(menu, R.id.action_balance, R.string.action_balance);
        setMenuTitle(menu, R.id.action_settings, R.string.action_settings);
        return true;
    }

    private void setMenuTitle(Menu menu, int itemId, int stringId) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setTitle(getString(stringId));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export) {
            // Export direkt hier ausführen – die Depot-Ansicht bleibt geöffnet (kein Wechsel zur Liste).
            doExport();
            return true;
        } else if (id == R.id.action_analysis) {
            if (depot != null && !depot.isEmpty()) {
                Intent i = new Intent(this, DepotChartActivity.class);
                i.putExtra(DepotChartActivity.EXTRA_DEPOT, depot);
                startActivity(i);
            }
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
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

    // ---- Export (in der Depot-Ansicht, wie in MainActivity) ----

    private void doExport() {
        if (settings.isKmyMode()) {
            runKmyExport();
            return;
        }
        if (!settings.hasRemoteConfig() && settings.getLocalExportTree().isEmpty()) {
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
                        Toast.makeText(DepotActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void runExport() {
        Toast.makeText(this, R.string.export_running, Toast.LENGTH_SHORT).show();
        String tree = settings.hasRemoteConfig() ? null : settings.getLocalExportTree();
        new ExportCoordinator(this, repository, settings, tree).exportUnexported((message, refreshNeeded) ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void showProgress(String text) {
        if (progressDialog != null) {
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

    // ---- Depot neu einlesen (Herunterziehen / langer Druck auf das Depot) ----

    /**
     * Lädt die .kmy und aktualisiert genau dieses Depot – im Hintergrund mit dem gelben Fortschrittsbanner
     * (wie im Hauptbildschirm); die Oberfläche bleibt bedienbar, nur bei Fehlern kommt eine Meldung.
     */
    private void reimportDepot(String depotName) {
        if (!settings.isKmyMode() || !settings.hasRemoteConfig()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        final String path = settings.getKmyPath();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }
        importStarted();
        new Thread(() -> {
            try {
                postImportProgress(getString(R.string.import_stage_download), 0);
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, getApplicationContext()), getApplicationContext());
                postImportProgress(getString(R.string.import_stage_depot, depotName), 50);
                KmyImporter.DepotData data = importer.importDepot(depotName);
                repository.replaceDepotImport(depotName, data.securities, data.transactions,
                        () -> runOnUiThread(this::completeImport));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    importFinished();
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ---- Gelber Import-Banner (Depot-Aktualisierung im Hintergrund) ----

    private int activeImports = 0;

    private void importStarted() {
        activeImports++;
        if (importBanner != null) {
            importBanner.setVisibility(View.VISIBLE);
            importShimmer.start();
            setImportProgress(getString(R.string.import_running_banner), 0);
        }
    }

    private void importFinished() {
        activeImports = Math.max(0, activeImports - 1);
        if (importBanner != null && activeImports == 0) {
            importShimmer.stop();
            importBanner.setVisibility(View.GONE);
        }
    }

    private void setImportProgress(String label, int percent) {
        if (importStatus != null) {
            importStatus.setText(label);
        }
        if (importPercent != null) {
            importPercent.setText(Math.max(0, Math.min(100, percent)) + " %");
        }
    }

    private void postImportProgress(String label, int percent) {
        runOnUiThread(() -> setImportProgress(label, percent));
    }

    /** Fertig: 100 % kurz zeigen, Banner ausblenden und Depot neu zeichnen. */
    private void completeImport() {
        setImportProgress(getString(R.string.import_stage_done), 100);
        importBanner.postDelayed(this::importFinished, 600);
        render();
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

    private void showFilterDialog() {
        View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_depot_filter, null, false);
        final com.google.android.material.textfield.TextInputEditText name =
                view.findViewById(R.id.depotFilterName);
        final com.google.android.material.slider.RangeSlider slider =
                view.findViewById(R.id.depotFilterSlider);
        final com.google.android.material.textfield.TextInputEditText from =
                view.findViewById(R.id.depotFilterFrom);
        final com.google.android.material.textfield.TextInputEditText to =
                view.findViewById(R.id.depotFilterTo);
        from.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        to.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        name.setText(filterName);

        // Wert-Range aus den aktuellen Beständen (nur sichtbare Positionen zählen).
        long dMin = Long.MAX_VALUE;
        long dMax = Long.MIN_VALUE;
        for (Repository.DepotHolding h : lastHoldings) {
            if (Math.abs(h.shares) < 1e-6) {
                continue;
            }
            if (h.valueCents < dMin) dMin = h.valueCents;
            if (h.valueCents > dMax) dMax = h.valueCents;
        }
        final long dataMin = dMin;
        final long dataMax = dMax;
        final boolean hasRange = dataMax > dataMin;
        if (hasRange) {
            float minE = dataMin / 100f;
            float maxE = dataMax / 100f;
            slider.setValueFrom(minE);
            slider.setValueTo(maxE);
            float curFrom = filterFrom != null ? filterFrom / 100f : minE;
            float curTo = filterTo != null ? filterTo / 100f : maxE;
            curFrom = Math.max(minE, Math.min(maxE, curFrom));
            curTo = Math.max(minE, Math.min(maxE, curTo));
            if (curFrom > curTo) {
                curFrom = minE;
                curTo = maxE;
            }
            slider.setValues(curFrom, curTo);
            slider.setLabelFormatter(value -> money(Math.round(value * 100)));

            final boolean[] syncing = {false};
            from.setText(centsPlain(Math.round(curFrom * 100)));
            to.setText(centsPlain(Math.round(curTo * 100)));

            slider.addOnChangeListener((s, val, fromUser) -> {
                if (syncing[0]) return;
                syncing[0] = true;
                java.util.List<Float> vals = s.getValues();
                from.setText(centsPlain(Math.round(vals.get(0) * 100)));
                to.setText(centsPlain(Math.round(vals.get(1) * 100)));
                syncing[0] = false;
            });

            android.text.TextWatcher tw = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable e) {
                    if (syncing[0]) return;
                    Long fromC = parseEuros(from.getText().toString());
                    Long toC = parseEuros(to.getText().toString());
                    if (fromC == null || toC == null) return;
                    float f = Math.max(dataMin, Math.min(dataMax, fromC)) / 100f;
                    float t = Math.max(dataMin, Math.min(dataMax, toC)) / 100f;
                    if (f > t) return;
                    syncing[0] = true;
                    slider.setValues(f, t);
                    syncing[0] = false;
                }
            };
            from.addTextChangedListener(tw);
            to.addTextChangedListener(tw);
        } else {
            slider.setValueFrom(0f);
            slider.setValueTo(1f);
            slider.setValues(0f, 1f);
            slider.setEnabled(false);
            if (filterFrom != null) from.setText(centsPlain(filterFrom));
            if (filterTo != null) to.setText(centsPlain(filterTo));
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.action_filter)
                .setView(view)
                .setNeutralButton(R.string.filter_reset, (d, w) -> {
                    filterName = "";
                    filterFrom = null;
                    filterTo = null;
                    render();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    filterName = name.getText() == null ? "" : name.getText().toString().trim();
                    if (hasRange) {
                        java.util.List<Float> vals = slider.getValues();
                        long fromC = Math.round(vals.get(0) * 100);
                        long toC = Math.round(vals.get(1) * 100);
                        if (fromC <= dataMin && toC >= dataMax) {
                            filterFrom = null;
                            filterTo = null;
                        } else {
                            filterFrom = fromC;
                            filterTo = toC;
                        }
                    } else {
                        filterFrom = parseEuros(from.getText() == null ? "" : from.getText().toString());
                        filterTo = parseEuros(to.getText() == null ? "" : to.getText().toString());
                    }
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Cent → „12,50" (ohne Währung), für die von/bis-Eingabefelder. */
    private static String centsPlain(long cents) {
        return de.spahr.ausgaben.settings.MoneyFormat.plain(cents);
    }

    /** „12,50" bzw. „12" → Cent; leer/ungültig → null. */
    private static Long parseEuros(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replace('.', ',');
        if (t.isEmpty()) {
            return null;
        }
        try {
            String[] parts = t.split(",", 2);
            long euros = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
            long cents = 0;
            if (parts.length == 2 && !parts[1].isEmpty()) {
                String c = (parts[1] + "00").substring(0, 2);
                cents = Long.parseLong(c);
            }
            return euros * 100 + cents;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Wertpapierliste ----

    private void openHistory(Repository.DepotHolding h) {
        Intent i = new Intent(this, SecurityHistoryActivity.class);
        i.putExtra(SecurityHistoryActivity.EXTRA_DEPOT, depot);
        i.putExtra(SecurityHistoryActivity.EXTRA_KMY_ID, h.kmyId);
        i.putExtra(SecurityHistoryActivity.EXTRA_NAME, h.name);
        i.putExtra(SecurityHistoryActivity.EXTRA_SECURITY_VALUE, h.valueCents);
        startActivity(i);
    }

    private void addRow(String label, String value, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 14, 0, 14);
        row.setClickable(true);
        row.setOnClickListener(onClick);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(15f);
        val.setGravity(Gravity.END);
        val.setTypeface(android.graphics.Typeface.MONOSPACE);

        row.addView(name);
        row.addView(val);
        container.addView(row);
    }

    private String money(long cents) {
        return de.spahr.ausgaben.settings.MoneyFormat.display(cents, Currencies.getDefault());
    }

    private static String shares(double v) {
        return trim(String.format(Locale.GERMANY, "%.4f", v));
    }

    private static String price(double v) {
        return trim(String.format(Locale.GERMANY, "%.4f", v));
    }

    private static String trim(String s) {
        if (s.indexOf(',') < 0) {
            return s;
        }
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && s.charAt(end - 1) == ',') {
            end--;
        }
        return s.substring(0, end);
    }
}
