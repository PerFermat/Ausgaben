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
import de.spahr.ausgaben.db.BookingSplit;
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
import de.spahr.ausgaben.voice.VoiceInput;

public class MainActivity extends AppCompatActivity {

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;

    private BookingAdapter adapter;
    private TextView textBalance;
    private TextView textSaldoLabel;

    private List<Booking> allBookings = new ArrayList<>();
    private java.util.Map<Long, List<BookingSplit>> splitsByBooking = new java.util.HashMap<>();
    private java.util.Map<String, Long> placeBalances = new java.util.LinkedHashMap<>();
    private long totalBalance = 0;
    private long allPlaceEntrySum = 0;
    private long filteredSum = 0;
    private final List<SaldoView> saldoViews = new ArrayList<>();
    private int saldoIndex = 0;

    private String filterPayee = "";
    private String filterCategory = "";
    private boolean filterCategoryIsMain = false;
    private Long filterAmountFrom = null;
    private Long filterAmountTo = null;

    private List<String> allCategories = new ArrayList<>();

    /** Gewähltes Konto (leer = „Alle Konten"). Standard beim Start = Standardkonto. */
    private String selectedAccount = "";
    private boolean accountInitialized = false;
    private long selectedAccountBalance = 0;
    /** Aktuell in der App vorhandene Konten (für „Alle Konten aktualisieren"). */
    private final List<String> appAccounts = new ArrayList<>();

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private androidx.recyclerview.widget.RecyclerView accountList;
    private AccountDrawerAdapter accountAdapter;

    /** Eine Sicht in der Saldo-Leiste. key: ACCOUNT | TOTAL | PLACE:<name> | FILTERED */
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
    public static final String VIEW_ACCOUNT_PREFIX = "ACCOUNT:";

    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Kein Logo mehr in der Toolbar; das kMyMoney-Logo sitzt im Kopf der Konten-Schublade.

        repository = new Repository(this);
        settings = new SettingsStore(this);
        placesStore = new PlacesStore(this);

        // Einmalige Migration: früher globale Orte + Ort-Bewegungen dem Standardkonto zuordnen.
        placesStore.migrateLegacyGlobalPlaces(settings.getDefaultAccount());
        repository.migratePlaceEntryAccounts(settings.getDefaultAccount());

        // Navigations-Schublade (Konten) links
        drawerLayout = findViewById(R.id.drawerLayout);
        androidx.appcompat.app.ActionBarDrawerToggle toggle =
                new androidx.appcompat.app.ActionBarDrawerToggle(this, drawerLayout, toolbar,
                        R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(getColor(R.color.white));

        accountList = findViewById(R.id.accountList);
        accountList.setLayoutManager(new LinearLayoutManager(this));
        accountAdapter = new AccountDrawerAdapter(getString(R.string.account_all),
                new AccountDrawerAdapter.Listener() {
                    @Override
                    public void onSelect(String account, boolean isAll) {
                        selectAccount(account);
                        drawerLayout.closeDrawers();
                    }

                    @Override
                    public void onImport(String account, boolean isAll) {
                        drawerLayout.closeDrawers();
                        onImportRequested(account, isAll);
                    }
                });
        accountList.setAdapter(accountAdapter);
        findViewById(R.id.addAccount).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            onAddAccountClicked();
        });

        textBalance = findViewById(R.id.textBalance);
        textSaldoLabel = findViewById(R.id.textSaldoLabel);
        findViewById(R.id.saldoHeader).setOnClickListener(v -> cycleSaldo());

        RecyclerView recycler = findViewById(R.id.recyclerBookings);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        // Haarfeine Trennlinien zwischen den Buchungen (kMyMoney-Ledger-Optik).
        com.google.android.material.divider.MaterialDividerItemDecoration divider =
                new com.google.android.material.divider.MaterialDividerItemDecoration(
                        this, com.google.android.material.divider.MaterialDividerItemDecoration.VERTICAL);
        divider.setDividerColor(getColor(R.color.list_divider));
        divider.setDividerThickness(Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
        divider.setDividerInsetStart(0);
        divider.setDividerInsetEnd(0);
        divider.setLastItemDecorated(false);
        recycler.addItemDecoration(divider);
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
        // Langer Druck → Buchung per Sprache anlegen (z. B. „Frisör 20€").
        fab.setOnLongClickListener(v -> {
            startVoiceEntry();
            return true;
        });

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
        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        java.util.ArrayList<String> spoken = result.getData()
                                .getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                        if (spoken != null && !spoken.isEmpty()) {
                            handleVoiceResult(spoken.get(0));
                            return;
                        }
                    }
                    Toast.makeText(this, R.string.voice_not_understood, Toast.LENGTH_SHORT).show();
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

    // ---- Buchung per Sprache (Lang-Druck auf FAB) ----

    private void startVoiceEntry() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
        try {
            voiceLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show();
        }
    }

    /** Zerlegt den gesprochenen Satz, sucht die passende Vorlage und öffnet den vorbefüllten Editor. */
    private void handleVoiceResult(String spoken) {
        VoiceInput.Result parsed = VoiceInput.parse(spoken);
        if (parsed.payee.isEmpty()) {
            Toast.makeText(this, R.string.voice_not_understood, Toast.LENGTH_SHORT).show();
            return;
        }
        final long amount = parsed.amountCents == null ? -1 : parsed.amountCents;
        repository.resolveVoice(parsed.payee, res -> {
            Intent i = new Intent(this, BookingEditActivity.class);
            if (res.booking != null) {
                i.putExtra(BookingEditActivity.EXTRA_TEMPLATE_BOOKING_ID, res.booking.id);
            } else {
                i.putExtra(BookingEditActivity.EXTRA_PREFILL_PAYEE, res.payee);
                // Ursprünglich Gesprochenes mitgeben: bei Änderung des Empfängers kann ein Alias gelernt
                // werden (der Name wurde in den Buchungen nicht gefunden).
                i.putExtra(BookingEditActivity.EXTRA_VOICE_SPOKEN_PAYEE, parsed.payee);
                if (res.alias != null) {
                    // Alias-Treffer: Buchung mit den hinterlegten Konto-/Kategorie-Daten vorbelegen.
                    i.putExtra(BookingEditActivity.EXTRA_ALIAS_ID, res.alias.id);
                }
                Toast.makeText(this, getString(R.string.voice_not_found, res.payee),
                        Toast.LENGTH_SHORT).show();
            }
            i.putExtra(BookingEditActivity.EXTRA_VOICE_AMOUNT_CENTS, amount);
            startActivity(i);
        });
    }

    private void refreshBookings() {
        repository.getCategoryNames(cats -> allCategories = cats);
        repository.getAccountNames(this::populateAccountDrawer);
        repository.getAllBookings(result -> {
            allBookings = result;
            repository.getAllSplitsMap(map -> {
                splitsByBooking = map;
                adapter.setSplits(map);
                reloadPlacesAndApply();
            });
        });
    }

    /** Lädt die Ort-Salden des aktuell gewählten Kontos und baut Liste/Saldo neu auf. */
    private void reloadPlacesAndApply() {
        repository.getPlaceBalances(selectedAccount, pb -> {
            placeBalances = new java.util.LinkedHashMap<>();
            allPlaceEntrySum = 0;
            for (PlaceBalance b : pb) {
                placeBalances.put(b.place, b.balanceCents);
                allPlaceEntrySum += b.balanceCents;
            }
            applyFilter();
        });
    }

    // ---- Konten-Schublade ----

    /** Füllt die Schublade mit „Alle Konten" + allen Konten; wählt beim ersten Mal das Standardkonto. */
    private void populateAccountDrawer(List<String> names) {
        appAccounts.clear();
        if (names != null) {
            appAccounts.addAll(names);
        }
        accountAdapter.setAccounts(names);
        if (!accountInitialized) {
            String def = settings.getDefaultAccount();
            selectedAccount = def == null ? "" : def.trim();
            accountInitialized = true;
        }
        updateAccountUi();
    }

    private void selectAccount(String name) {
        selectedAccount = name == null ? "" : name;
        accountInitialized = true;
        saldoIndex = 0;
        updateAccountUi();
        reloadPlacesAndApply();
    }

    /** Aktualisiert Toolbar-Titel und markiert den gewählten Schubladen-Eintrag. */
    private void updateAccountUi() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(
                    selectedAccount.isEmpty() ? getString(R.string.account_all) : selectedAccount);
        }
        accountAdapter.setSelected(selectedAccount);
    }

    // ---- Filter ----

    private void applyFilter() {
        List<Booking> filtered = new ArrayList<>();
        java.util.Map<Long, Long> amountOverride = new java.util.HashMap<>();
        filteredSum = 0;
        totalBalance = 0;
        selectedAccountBalance = 0;
        for (Booking b : allBookings) {
            long signed = b.isIncome ? b.amountCents : -b.amountCents;
            totalBalance += signed;
            if (selectedAccount.isEmpty() || b.account.equalsIgnoreCase(selectedAccount)) {
                selectedAccountBalance += signed;
            }
            if (matchesFilter(b)) {
                long disp = displaySignedForFilter(b, signed);
                if (disp != signed) {
                    amountOverride.put(b.id, disp);
                }
                filtered.add(b);
                filteredSum += disp;
            }
        }
        adapter.setAmountOverride(amountOverride);
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
        // Konto ist jetzt die primäre Auswahl (Schublade), „" = alle Konten.
        if (!selectedAccount.isEmpty() && !b.account.equalsIgnoreCase(selectedAccount)) {
            return false;
        }
        if (!filterPayee.isEmpty()
                && !b.payee.toLowerCase(Locale.GERMANY).contains(filterPayee.toLowerCase(Locale.GERMANY))) {
            return false;
        }
        if (!filterCategory.isEmpty() && !categoryMatchesBooking(b)) {
            return false;
        }
        if (filterAmountFrom != null && b.amountCents < filterAmountFrom) {
            return false;
        }
        return filterAmountTo == null || b.amountCents <= filterAmountTo;
    }

    /**
     * Bei aktivem Kategorie-Filter zeigt eine Splitbuchung nur den Teilbetrag der gewählten Kategorie;
     * sonst den vollen (vorzeichenbehafteten) Betrag {@code full}.
     */
    private long displaySignedForFilter(Booking b, long full) {
        if (filterCategory.isEmpty()) {
            return full;
        }
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts == null || parts.isEmpty()) {
            return full; // Einzelkategorie: gesamter Betrag entfällt auf diese Kategorie
        }
        long sum = 0;
        boolean any = false;
        for (BookingSplit p : parts) {
            if (categoryMatches(p.category)) {
                sum += b.isIncome ? p.amountCents : -p.amountCents;
                any = true;
            }
        }
        return any ? sum : full;
    }

    /** Treffer, wenn die (Haupt-)Kategorie oder eine Teilkategorie einer Splitbuchung passt. */
    private boolean categoryMatchesBooking(Booking b) {
        if (categoryMatches(b.category)) {
            return true;
        }
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts != null) {
            for (BookingSplit p : parts) {
                if (categoryMatches(p.category)) {
                    return true;
                }
            }
        }
        return false;
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
        return !filterPayee.isEmpty() || !filterCategory.isEmpty()
                || filterAmountFrom != null || filterAmountTo != null;
    }

    // ---- Saldo-Leiste (Durchschalten) ----

    private void buildSaldoViews() {
        saldoViews.clear();
        // 1. Saldo des gewählten Kontos (außer bei „Alle Konten")
        if (!selectedAccount.isEmpty()) {
            saldoViews.add(new SaldoView(VIEW_ACCOUNT_PREFIX + selectedAccount, selectedAccount,
                    selectedAccountBalance));
        }
        // 2. Gesamt (alle Konten)
        saldoViews.add(new SaldoView(VIEW_TOTAL, getString(R.string.saldo_total), totalBalance));
        // 3. Orte des gewählten Kontos (Standardort = Rest-Topf des Kontos).
        if (!selectedAccount.isEmpty()) {
            List<String> places = placesStore.getPlaces(selectedAccount);
            String standardort = placesStore.getDefaultPlace(selectedAccount);
            long otherSum = 0;
            for (String place : places) {
                if (!place.equals(standardort)) {
                    otherSum += placeBalances.containsKey(place) ? placeBalances.get(place) : 0L;
                }
            }
            for (String place : places) {
                long bal = place.equals(standardort)
                        ? selectedAccountBalance - otherSum
                        : (placeBalances.containsKey(place) ? placeBalances.get(place) : 0L);
                if (bal != 0) {
                    saldoViews.add(new SaldoView(VIEW_PLACE_PREFIX + place, place, bal));
                }
            }
        }
        // 4. Gefiltert
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
        MaterialAutoCompleteTextView fCategory = view.findViewById(R.id.filterCategory);
        com.google.android.material.slider.RangeSlider slider = view.findViewById(R.id.filterAmountSlider);
        TextInputEditText fFrom = view.findViewById(R.id.filterAmountFrom);
        TextInputEditText fTo = view.findViewById(R.id.filterAmountTo);
        fFrom.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        fTo.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        repository.getPayeeNames(names -> fPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        fPayee.setText(filterPayee);

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
        if (id == R.id.action_export) {
            doExport();
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        } else if (id == R.id.action_analysis) {
            Intent i = new Intent(this, AnalysisActivity.class);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_PAYEE, filterPayee);
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

    // ---- Import (Schublade: langer Tipp = importieren) ----

    /** Langer Tipp auf ein Konto (bzw. „Alle Konten") in der Schublade. */
    private void onImportRequested(String account, boolean isAll) {
        if (!settings.isKmyMode()) {
            startCsvImport();
            return;
        }
        if (!settings.hasNextcloudConfig()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        if (settings.getKmyPath().isEmpty()) {
            Toast.makeText(this, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setNegativeButton(R.string.cancel, null);
        if (isAll) {
            b.setTitle(R.string.kmy_import_all_title)
                    .setMessage(R.string.kmy_import_all_message)
                    .setPositiveButton(R.string.kmy_import_replace, (d, w) -> runKmyImport(null));
        } else {
            b.setTitle(R.string.kmy_replace_title)
                    .setMessage(getString(R.string.kmy_replace_message, account))
                    .setPositiveButton(R.string.kmy_import_replace, (d, w) -> runKmyImport(account));
        }
        b.show();
    }

    /** „Neues Konto hinzufügen": lädt die .kmy und zeigt den Konto-Auswahldialog. */
    private void onAddAccountClicked() {
        if (!settings.isKmyMode()) {
            startCsvImport();
            return;
        }
        if (!settings.hasNextcloudConfig()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        String path = settings.getKmyPath();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }
        showProgress(getString(R.string.progress_download));
        new Thread(() -> {
            try {
                byte[] raw = new NextcloudUploader(settings.isNextcloudServer()).downloadBytes(settings.getUrl(),
                        settings.getUser(), settings.getPassword(), folderOf(path), fileOf(path));
                KmyImporter importer = new KmyImporter(new KmyDocument(raw));
                runOnUiThread(() -> {
                    dismissProgress();
                    List<String> accounts = importer.accountNames();
                    if (accounts.isEmpty()) {
                        Toast.makeText(this, R.string.kmy_no_files, Toast.LENGTH_LONG).show();
                    } else {
                        chooseAccountForImport(importer, accounts);
                    }
                });
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    private void chooseAccountForImport(KmyImporter importer, List<String> accounts) {
        String[] items = accounts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.kmy_choose_account)
                .setItems(items, (d, w) -> {
                    showProgress(getString(R.string.progress_importing));
                    List<String> one = new ArrayList<>();
                    one.add(items[w]);
                    new Thread(() -> replaceFromImporter(importer, one)).start();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Lädt die .kmy und importiert ein Konto ({@code null} = alle bereits vorhandenen App-Konten). */
    private void runKmyImport(final String account) {
        showProgress(getString(R.string.progress_download));
        new Thread(() -> {
            KmyImporter importer;
            try {
                String path = settings.getKmyPath();
                byte[] raw = new NextcloudUploader(settings.isNextcloudServer()).downloadBytes(settings.getUrl(),
                        settings.getUser(), settings.getPassword(), folderOf(path), fileOf(path));
                importer = new KmyImporter(new KmyDocument(raw));
            } catch (Exception e) {
                postImportError(e);
                return;
            }
            List<String> available = importer.accountNames();
            List<String> targets = new ArrayList<>();
            if (account == null) {
                // Nur bereits vorhandene App-Konten, die es auch in der .kmy gibt (keine neuen anlegen).
                for (String acc : appAccounts) {
                    if (containsIgnoreCase(available, acc)) {
                        targets.add(acc);
                    }
                }
            } else if (containsIgnoreCase(available, account)) {
                targets.add(account);
            }
            if (targets.isEmpty()) {
                runOnUiThread(() -> {
                    dismissProgress();
                    Toast.makeText(this, R.string.kmy_account_not_found, Toast.LENGTH_LONG).show();
                });
                return;
            }
            runOnUiThread(() -> updateProgress(getString(R.string.progress_importing)));
            replaceFromImporter(importer, targets);
        }).start();
    }

    /** Baut die Buchungen der Zielkonten und ersetzt sie in der DB. Läuft im Hintergrund-Thread. */
    private void replaceFromImporter(KmyImporter importer, List<String> accounts) {
        try {
            java.util.LinkedHashMap<String, List<Booking>> map = new java.util.LinkedHashMap<>();
            for (String acc : accounts) {
                map.put(acc, importer.bookingsForAccount(acc));
            }
            runOnUiThread(() -> repository.replaceImportAccounts(map, res -> {
                dismissProgress();
                Toast.makeText(this, getString(R.string.kmy_import_done_multi, res[1], res[0]),
                        Toast.LENGTH_LONG).show();
                refreshBookings();
            }));
        } catch (Exception e) {
            postImportError(e);
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String name) {
        for (String s : list) {
            if (s.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void postImportError(Exception e) {
        final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        runOnUiThread(() -> {
            dismissProgress();
            Toast.makeText(this, getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show();
        });
    }

    // ---- CSV-Import (Nextcloud-Liste oder lokaler Picker) ----

    private void startCsvImport() {
        if (settings.hasNextcloudConfig()) {
            loadNextcloudFileList();
        } else {
            importLauncher.launch(new String[]{
                    "text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"});
        }
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
                List<String> files = new NextcloudUploader(settings.isNextcloudServer()).listCsvFiles(
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
                String content = new NextcloudUploader(settings.isNextcloudServer()).downloadText(
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
