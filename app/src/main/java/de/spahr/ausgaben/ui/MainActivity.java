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
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;
import de.spahr.ausgaben.voice.VoiceInput;

public class MainActivity extends LocalizedActivity {

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;

    private BookingAdapter adapter;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private TextView textBalance;
    private TextView textSaldoLabel;
    private View importBanner;
    private ShimmerView importShimmer;
    private TextView importStatus;
    private TextView importPercent;
    private int activeImports = 0;   // laufende Hintergrund-Importe (Banner sichtbar solange > 0)

    /** Uhr-Buchung wurde im Hintergrund angelegt → Liste live aktualisieren. */
    private final android.content.BroadcastReceiver bookingsChangedReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, Intent intent) {
                    refreshBookings();
                }
            };

    private List<Booking> allBookings = new ArrayList<>();
    private java.util.Map<Long, List<BookingSplit>> splitsByBooking = new java.util.HashMap<>();
    private java.util.Map<String, Long> placeBalances = new java.util.LinkedHashMap<>();
    private long totalBalance = 0;
    private long depotValueCents = 0;
    private long allPlaceEntrySum = 0;
    private long filteredSum = 0;
    private final List<SaldoView> saldoViews = new ArrayList<>();
    private int saldoIndex = 0;

    private String filterPayee = "";
    private String filterCategory = "";
    private boolean filterCategoryIsMain = false;
    /** Typ der gefilterten Kategorie (Einnahme/Ausgabe), {@code null} = kein Typ gewählt ("Alle"). */
    private Boolean filterCategoryIsIncome = null;
    private Long filterAmountFrom = null;
    private Long filterAmountTo = null;
    private Long filterDateFrom = null;
    private Long filterDateTo = null;

    private List<String> catExpense = new ArrayList<>();
    private List<String> catIncome = new ArrayList<>();
    /** Kategorie-Pfad → Typ (globaler Rückfall für Buchungszeilen ohne eigenen Typ, siehe Booking#categoryIsIncome). */
    private java.util.Map<String, Boolean> categoryTypes = new java.util.HashMap<>();

    /** Gewähltes Konto (leer = „Alle Konten"). Standard beim Start = Standardkonto. */
    private String selectedAccount = "";
    /** true, wenn Depotdaten importiert wurden (steuert das „Depot"-Menü). */
    private boolean hasDepot = false;
    private boolean accountInitialized = false;
    /** true, sobald das On-Boarding in dieser App-Sitzung einmal automatisch gezeigt wurde. */
    private boolean onboardingShown = false;
    private long selectedAccountBalance = 0;
    /** Aktuell in der App vorhandene Konten (für „Alle Konten aktualisieren"). */
    private final List<String> appAccounts = new ArrayList<>();
    /** Alle bereits importierten Konten inkl. geschlossener – zum Ausblenden im Import-Auswahldialog. */
    private final List<String> importedAccounts = new ArrayList<>();
    /** Bereits importierte Depots – um sie im Import-Auswahldialog auszublenden. */
    private final List<String> appDepots = new ArrayList<>();

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

    /** Extra: dieses Konto beim Start auswählen (z. B. aus der Depot-Schublade). */
    public static final String EXTRA_SELECT_ACCOUNT = "select_account";
    /** Extra: nach dem Start sofort den Export/Sync ausführen (z. B. aus dem Depot-Menü). */
    public static final String EXTRA_RUN_EXPORT = "run_export";
    /** Extra: Launcher-Shortcut „Neue Ausgabe" – öffnet direkt den Buchungs-Editor (siehe xml/shortcuts.xml). */
    public static final String EXTRA_NEW_BOOKING = "de.spahr.ausgaben.NEW_BOOKING";
    /** Extra: aus dem Homescreen-Widget angeforderte Aktion (Werte {@code WIDGET_ACTION_*}). */
    public static final String EXTRA_WIDGET_ACTION = "widget_action";
    public static final String WIDGET_ACTION_NEW = "new";
    public static final String WIDGET_ACTION_VOICE = "voice";
    public static final String WIDGET_ACTION_DIGITS = "digits";
    public static final String WIDGET_ACTION_BALANCES = "balances";

    public static final String VIEW_TOTAL = "TOTAL";
    public static final String VIEW_NETWORTH = "NETWORTH";
    public static final String VIEW_NOPLACE = "NOPLACE";
    public static final String VIEW_FILTERED = "FILTERED";
    public static final String VIEW_PLACE_PREFIX = "PLACE:";
    public static final String VIEW_ACCOUNT_PREFIX = "ACCOUNT:";

    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;
    private ActivityResultLauncher<Intent> editLauncher;
    private ActivityResultLauncher<String> locationPermissionLauncher;

    /** Aktueller Standort für die Betrag-only-Auflösung (rein lokal, nur Koordinaten). */
    private de.spahr.ausgaben.location.LocationTagger locationTagger;

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

        // Von der Depot-Schublade aus kann ein Konto vorgewählt werden.
        String preselect = getIntent().getStringExtra(EXTRA_SELECT_ACCOUNT);
        if (preselect != null) {
            selectedAccount = preselect;
            accountInitialized = true;
        }

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
                        // Schublade offen lassen; Import-Dialog/Datei-Browser erscheint darüber.
                        onImportRequested(account, isAll);
                    }

                    @Override
                    public void onDepotSelect(String depot) {
                        drawerLayout.closeDrawers();
                        Intent i = new Intent(MainActivity.this, DepotActivity.class);
                        i.putExtra(DepotActivity.EXTRA_DEPOT, depot);
                        startActivity(i);
                    }

                    @Override
                    public void onDepotImport(String depot) {
                        // Schublade offen lassen; Bestätigungsdialog erscheint darüber.
                        reimportDepot(depot);
                    }
                });
        accountList.setAdapter(accountAdapter);
        findViewById(R.id.addAccount).setOnClickListener(v -> onAddAccountClicked());

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
        adapter.setListener(new BookingAdapter.Listener() {
            @Override
            public void onClick(Booking b) {
                // Kurzer Druck: Buchung nur ansehen (ohne Änderungsmöglichkeit).
                Intent i = new Intent(MainActivity.this, BookingEditActivity.class);
                i.putExtra(BookingEditActivity.EXTRA_BOOKING_ID, b.id);
                i.putExtra(BookingEditActivity.EXTRA_READ_ONLY, true);
                startActivity(i);
            }

            @Override
            public void onLongClick(Booking b) {
                // Langer Druck: Buchung bearbeiten. Über den Launcher, damit nach einem Löschen
                // „Rückgängig" angeboten werden kann.
                Intent i = new Intent(MainActivity.this, BookingEditActivity.class);
                i.putExtra(BookingEditActivity.EXTRA_BOOKING_ID, b.id);
                editLauncher.launch(i);
            }
        });
        recycler.setAdapter(adapter);

        // Wischgeste nach unten: in der kmy-Variante das aktuelle Konto neu aus der .kmy einlesen,
        // sonst nur die DB neu anzeigen (in der CSV-Variante nur übers Kontenmenü aktualisierbar).
        importBanner = findViewById(R.id.importBanner);
        importShimmer = findViewById(R.id.importShimmer);
        importStatus = findViewById(R.id.importStatus);
        importPercent = findViewById(R.id.importPercent);
        importShimmer.setColors(getColor(R.color.import_banner_bg), getColor(R.color.import_banner_shimmer));
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            swipeRefresh.setRefreshing(false);
            if (settings.isKmyMode() && !selectedAccount.isEmpty()
                    && settings.hasRemoteConfig() && !settings.getKmyPath().isEmpty()) {
                runKmyImport(selectedAccount);
            } else {
                refreshBookings();
            }
        });

        ExtendedFloatingActionButton fab = findViewById(R.id.fabNew);
        fab.setOnClickListener(v -> {
            Intent i = new Intent(this, BookingEditActivity.class);
            // Ist ein einzelnes Konto in der Ansicht, die neue Buchung auf jeden Fall dort anlegen.
            if (!selectedAccount.isEmpty()) {
                i.putExtra(BookingEditActivity.EXTRA_PRESET_ACCOUNT, selectedAccount);
            }
            startActivity(i);
        });
        // Langer Druck → Buchung per Sprache anlegen (z. B. „Frisör 20€").
        fab.setOnLongClickListener(v -> {
            startVoiceEntry();
            return true;
        });

        // Ziffern-Symbol: stille Betrag-Eingabe (ohne Mikrofon) → Auflösung per Standort.
        findViewById(R.id.fabNumber).setOnClickListener(v -> showNumberEntry());

        // Standort für die Betrag-only-Auflösung (nur Koordinaten, rein lokal – wie im Editor).
        locationTagger = new de.spahr.ausgaben.location.LocationTagger(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        locationTagger.start();
                    }
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
        // Buchungs-Editor: liefert nach dem Löschen die Daten für „Rückgängig" zurück.
        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        showUndoDelete(result.getData());
                    }
                });
        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        java.util.ArrayList<String> spoken = result.getData()
                                .getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                        if (spoken != null && !spoken.isEmpty()) {
                            handleVoiceResult(pickBestSpoken(spoken));
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

    /**
     * Zeigt nach dem Löschen „Rückgängig" an und legt die Buchung auf Wunsch wieder an. Sie bekommt dabei
     * eine neue id, und im Ort-Journal bleiben Löschung und Wiederanlage als Bewegungen stehen – so
     * arbeitet das Journal ohnehin (die Historie bleibt erhalten, der Saldo stimmt).
     */
    private void showUndoDelete(Intent data) {
        final Bundle u = data == null ? null : data.getBundleExtra(BookingEditActivity.EXTRA_UNDO_BOOKING);
        if (u == null) {
            return;
        }
        com.google.android.material.snackbar.Snackbar
                .make(findViewById(android.R.id.content), R.string.booking_deleted,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> restoreBooking(u))
                .show();
    }

    private void restoreBooking(Bundle u) {
        Booking b = new Booking();
        b.payee = u.getString("payee", "");
        b.account = u.getString("account", "");
        b.category = u.getString("category", "");
        b.note = u.getString("note", "");
        b.amountCents = u.getLong("amount");
        b.isIncome = u.getBoolean("income");
        b.createdAt = u.getLong("created");
        b.exported = u.getBoolean("exported");
        final String place = u.getString("place", "");
        final Runnable done = () -> {
            refreshBookings();
            Toast.makeText(this, R.string.booking_restored, Toast.LENGTH_SHORT).show();
        };
        java.util.ArrayList<String> cats = u.getStringArrayList("splitCats");
        long[] amounts = u.getLongArray("splitAmounts");
        if (cats != null && amounts != null && cats.size() >= 2 && amounts.length == cats.size()) {
            List<de.spahr.ausgaben.db.BookingSplit> parts = new ArrayList<>();
            for (int i = 0; i < cats.size(); i++) {
                parts.add(new de.spahr.ausgaben.db.BookingSplit(0, cats.get(i), amounts[i]));
            }
            repository.saveSplitBooking(b, parts, place, done);
        } else {
            repository.saveBookingWithPlace(b, place, done);
        }
    }

    /**
     * Launcher-Shortcut „Neue Ausgabe": Der Shortcut zielt auf diese (exportierte) Activity – die
     * BookingEditActivity ist nicht exportiert und dürfte vom Launcher nicht direkt gestartet werden.
     * Das Extra wird nach dem Öffnen entfernt, damit der Editor bei jedem Zurück nicht erneut aufgeht.
     */
    private void handleShortcutIntent() {
        if (getIntent() == null || !getIntent().getBooleanExtra(EXTRA_NEW_BOOKING, false)) {
            return;
        }
        getIntent().removeExtra(EXTRA_NEW_BOOKING);
        Intent i = new Intent(this, BookingEditActivity.class);
        if (!selectedAccount.isEmpty()) {
            i.putExtra(BookingEditActivity.EXTRA_PRESET_ACCOUNT, selectedAccount);
        }
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleShortcutIntent();
        androidx.core.content.ContextCompat.registerReceiver(this, bookingsChangedReceiver,
                new android.content.IntentFilter(
                        de.spahr.ausgaben.wear.WearBridge.ACTION_BOOKINGS_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        de.spahr.ausgaben.settings.Currencies.refresh(this);
        de.spahr.ausgaben.settings.MoneyFormat.refresh(this);
        // Offene Belegfotos im Hintergrund ins Netzlaufwerk hochladen (No-op ohne offene/ohne Config).
        de.spahr.ausgaben.receipt.ReceiptSync.syncPending(this);
        boolean gps = settings.isGpsEnabled();
        // Ziffern-Button (stille Betrag-only-Erfassung) nur bei aktivem Standort anbieten.
        findViewById(R.id.fabNumber).setVisibility(gps ? View.VISIBLE : View.GONE);
        if (gps && locationTagger != null && hasLocationPermission()) {
            locationTagger.start();
        }
        // Depots in die Kontenschublade übernehmen und den Depotwert (für „Gesamtvermögen") laden.
        repository.getDepots(depots -> {
            hasDepot = !depots.isEmpty();
            appDepots.clear();
            appDepots.addAll(depots);
            accountAdapter.setDepots(depots);
            loadDepotTotal(depots);
        });
        refreshBookings();
        // Standardort-Saldo an die Uhr spiegeln (No-op im foss-Flavor; nur bei Änderung übertragen).
        de.spahr.ausgaben.wear.BalanceSync.publish(this);
        // Homescreen-Widgets mit dem aktuellen Saldo/den letzten Buchungen versorgen.
        de.spahr.ausgaben.widget.AusgabenWidget.refreshAll(this);
        // Export/Sync aus dem Depot-Menü nachholen.
        if (getIntent().getBooleanExtra(EXTRA_RUN_EXPORT, false)) {
            getIntent().removeExtra(EXTRA_RUN_EXPORT);
            doExport();
        }
        // Aus dem Homescreen-Widget angeforderte Aktion ausführen (nach dem Entsperren).
        String widgetAction = getIntent().getStringExtra(EXTRA_WIDGET_ACTION);
        if (widgetAction != null) {
            getIntent().removeExtra(EXTRA_WIDGET_ACTION);
            handleWidgetAction(widgetAction);
        }
    }

    /** Führt die vom Homescreen-Widget angeforderte Schnellaktion aus. */
    private void handleWidgetAction(String action) {
        switch (action) {
            case WIDGET_ACTION_VOICE:
                startVoiceEntry();
                break;
            case WIDGET_ACTION_DIGITS:
                showNumberEntry();
                break;
            case WIDGET_ACTION_BALANCES:
                startActivity(new Intent(this, BalanceActivity.class));
                break;
            case WIDGET_ACTION_NEW:
            default: {
                Intent i = new Intent(this, BookingEditActivity.class);
                if (!selectedAccount.isEmpty()) {
                    i.putExtra(BookingEditActivity.EXTRA_PRESET_ACCOUNT, selectedAccount);
                }
                startActivity(i);
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(bookingsChangedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (locationTagger != null) {
            locationTagger.stop();
        }
    }

    // ---- Buchung per Sprache (Lang-Druck auf FAB) ----

    private void startVoiceEntry() {
        // Kein Vorab-Check über SpeechRecognizer.isRecognitionAvailable(): der prüft einen eigenen
        // RecognitionService-Dienst und liefert auf manchen Geräten fälschlich „nicht verfügbar",
        // obwohl z. B. die Google-Suchleiste per Mikrofon-Symbol einwandfrei funktioniert (die nutzt die
        // gleiche ACTION_RECOGNIZE_SPEECH-Activity). Stattdessen wird direkt versucht zu starten; schlägt
        // das fehl, fängt der catch-Block unten (ActivityNotFoundException) das echte „kein Erkenner" ab.
        // Standort für eine mögliche Betrag-only-Auflösung vorwärmen (nur bei aktivem GPS).
        if (settings.isGpsEnabled() && locationTagger != null && !hasLocationPermission()) {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Erkennungssprache folgt der gewählten App-Sprache (auch hochgeladene); nicht unterstützte
        // Codes fallen im System auf die Gerätesprache zurück.
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, settings.getLanguage());
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
        // Mehrere Alternativen anfordern (die erste mit lesbarem Betrag wird bevorzugt).
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        try {
            voiceLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_no_recognizer, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Zerlegt den gesprochenen Satz und öffnet den vorbefüllten Editor. Mit Empfänger wird über
     * Aliase/Buchungen aufgelöst; bei <b>reinem Betrag</b> über den aktuellen Standort (100 m).
     */
    private void handleVoiceResult(String spoken) {
        VoiceInput.Result parsed = VoiceInput.parse(spoken);
        final long amount = parsed.amountCents == null ? -1 : parsed.amountCents;
        if (parsed.payee.isEmpty()) {
            // Reiner Betrag ohne Standort ist am Handy nicht auflösbar → bei GPS aus abweisen.
            if (amount <= 0 || !settings.isGpsEnabled()) {
                Toast.makeText(this, R.string.voice_not_understood, Toast.LENGTH_SHORT).show();
                return;
            }
            // Nur Betrag → per Standort auflösen (kein Treffer → Editor nur mit Betrag).
            String coords = locationTagger != null ? locationTagger.currentCoordinates() : null;
            repository.resolveVoiceByGps(coords, res -> openVoiceEditor(res, amount, ""));
            return;
        }
        // Aktuelle Position mitgeben (nur bei GPS an) → bei mehreren gleichnamigen Empfängern der nächste.
        String coords = settings.isGpsEnabled() && locationTagger != null
                ? locationTagger.currentCoordinates() : null;
        repository.resolveVoice(parsed.payee, coords, res -> openVoiceEditor(res, amount, parsed.payee));
    }

    /** Wählt aus den Erkennungs-Alternativen die erste, aus der ein Betrag lesbar ist (sonst die beste),
     * damit ein Betrag nicht verloren geht, falls das Top-Ergebnis keine Zahl enthält. */
    private String pickBestSpoken(java.util.List<String> list) {
        for (String s : list) {
            if (s != null && VoiceInput.parse(s).amountCents != null) {
                return s;
            }
        }
        return list.get(0);
    }

    /** Öffnet den Editor vorbelegt aus einer Auflösung (Vorlage/Alias) + Betrag. */
    private void openVoiceEditor(Repository.VoiceResolution res, long amount, String spokenPayee) {
        Intent i = new Intent(this, BookingEditActivity.class);
        // Angezeigtes Konto ist eine Nutzereingabe: steckt es bei einer Umbuchung bereits als Von- oder
        // Nach-Konto in Alias/Vorlage, bleiben dort beide Konten unverändert; sonst ersetzt es das
        // Von-Konto (nur bei „Alle Konten" – selectedAccount leer – bleibt Alias/Vorlage maßgeblich).
        if (!selectedAccount.isEmpty()) {
            i.putExtra(BookingEditActivity.EXTRA_PRESET_TRANSFER_FROM_ACCOUNT, selectedAccount);
        }
        if (res.booking != null) {
            i.putExtra(BookingEditActivity.EXTRA_TEMPLATE_BOOKING_ID, res.booking.id);
        } else {
            if (!res.payee.isEmpty()) {
                i.putExtra(BookingEditActivity.EXTRA_PREFILL_PAYEE, res.payee);
            }
            if (res.alias != null) {
                i.putExtra(BookingEditActivity.EXTRA_ALIAS_ID, res.alias.id);
            } else if (!selectedAccount.isEmpty()) {
                // Kein Alias/keine Vorlage liefert ein Konto → angezeigtes Konto vorbelegen.
                i.putExtra(BookingEditActivity.EXTRA_PRESET_ACCOUNT, selectedAccount);
            }
            if (spokenPayee != null && !spokenPayee.isEmpty()) {
                // Ursprünglich Gesprochenes: bei Änderung des Empfängers kann ein Alias gelernt werden.
                i.putExtra(BookingEditActivity.EXTRA_VOICE_SPOKEN_PAYEE, spokenPayee);
                Toast.makeText(this, getString(R.string.voice_not_found, res.payee),
                        Toast.LENGTH_SHORT).show();
            }
        }
        i.putExtra(BookingEditActivity.EXTRA_VOICE_AMOUNT_CENTS, amount);
        startActivity(i);
    }

    /**
     * Stille Zifferneingabe: Betrag eintippen → Betrag-only-Pfad (Auflösung per Standort). Der anhand der
     * aktuellen GPS-Position ermittelte Empfänger wird live unter dem Betrag angezeigt.
     */
    private void showNumberEntry() {
        if (locationTagger != null && !hasLocationPermission()) {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        final com.google.android.material.textfield.TextInputEditText field =
                new com.google.android.material.textfield.TextInputEditText(this);
        field.setHint(R.string.amount_hint);
        // Ziffernblock, der auch + und * anbietet (kleine Rechnung wie 10+20*3); erlaubte Zeichen/Struktur
        // regelt der CalcInputFilter. Ausgewertet wird beim Speichern über AmountExpression.
        field.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        field.setFilters(new android.text.InputFilter[]{new CalcInputFilter()});

        final android.widget.TextView payeeView = new android.widget.TextView(this);
        payeeView.setText(getString(R.string.voice_payee_resolved, "—"));

        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        payeeView.setPadding(0, pad / 2, 0, 0);
        box.addView(payeeView);

        // Empfänger anhand der aktuellen Position ermitteln und anzeigen (aktualisiert sich bei neuem Fix).
        final Repository.VoiceResolution[] lastRes = new Repository.VoiceResolution[1];
        final Runnable resolveShow = () -> {
            String coords = settings.isGpsEnabled() && locationTagger != null
                    ? locationTagger.currentCoordinates() : null;
            if (coords == null) {
                return;
            }
            repository.resolveVoiceByGps(coords, res -> {
                lastRes[0] = res;
                String name = res != null && res.payee != null && !res.payee.isEmpty() ? res.payee : "—";
                payeeView.setText(getString(R.string.voice_payee_resolved, name));
            });
        };
        resolveShow.run();
        if (locationTagger != null) {
            locationTagger.setOnLocationUpdate(resolveShow::run);
        }

        // Einziges Feld im Dialog: OK auf der Rechentastatur übernimmt direkt (kein separater
        // Speichern-Knopf nötig) – Rechnung ist bereits ausgewertet, wenn valid == true.
        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];
        final CalcKeyboardView calc = new CalcKeyboardView(this);
        calc.attachTo(field);
        calc.setOnOk(valid -> {
            if (!valid) {
                Toast.makeText(this, R.string.error_amount_calc, Toast.LENGTH_SHORT).show();
                return;
            }
            String raw = field.getText() == null ? "" : field.getText().toString().trim();
            if (raw.isEmpty()) {
                return;
            }
            Long cents = de.spahr.ausgaben.settings.AmountExpression.toCents(raw);
            if (cents == null || cents <= 0) {
                Toast.makeText(this, R.string.error_amount_calc, Toast.LENGTH_SHORT).show();
                return;
            }
            String amt = de.spahr.ausgaben.settings.MoneyFormat.plain(cents);
            if (lastRes[0] != null) {
                // Bereits per Standort ermittelten Empfänger wiederverwenden (konsistent mit Anzeige).
                openVoiceEditor(lastRes[0], cents, "");
            } else {
                handleVoiceResult(amt); // payee leer + Betrag → Betrag-only-Pfad
            }
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        field.requestFocus();
        box.addView(calc);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.new_booking)
                .setView(box)
                .setOnDismissListener(d -> {
                    if (locationTagger != null) {
                        locationTagger.setOnLocationUpdate(null);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialogRef[0] = dialog;
        // Nur die eigene Rechentastatur zeigen – die System-Tastatur des Dialogs unterdrücken.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.show();
    }

    private boolean hasLocationPermission() {
        return androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                || androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void refreshBookings() {
        repository.getCategoriesGrouped(g -> {
            catExpense = g.expense;
            catIncome = g.income;
        });
        repository.getCategoryTypes(types -> categoryTypes = types);
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
        // Alle bereits importierten Konten (auch geschlossene) merken – zum Ausblenden im Import-Dialog.
        repository.getAllAccountNames(all -> {
            importedAccounts.clear();
            importedAccounts.addAll(all);
        });
        // Schublade nach Anlage-/Verbindlichkeitskonten gruppieren.
        repository.getAccountsGrouped(g -> accountAdapter.setAccounts(g.assets, g.liabilities));
        if (!accountInitialized) {
            String def = settings.getDefaultAccount();
            selectedAccount = def == null ? "" : def.trim();
            accountInitialized = true;
        }
        updateAccountUi();
        // On-Boarding automatisch anstoßen, solange noch keine Konten existieren (nur einmal je Sitzung).
        if (!onboardingShown && (names == null || names.isEmpty())) {
            onboardingShown = true;
            startActivity(new Intent(this, OnboardingActivity.class));
        }
    }

    private void selectAccount(String name) {
        selectedAccount = name == null ? "" : name;
        accountInitialized = true;
        saldoIndex = 0;
        updateAccountUi();
        reloadPlacesAndApply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Kontowahl aus der Depot-Schublade übernehmen, wenn MainActivity wiederverwendet wird.
        String sel = intent.getStringExtra(EXTRA_SELECT_ACCOUNT);
        if (sel != null) {
            selectAccount(sel);
        }
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
        // Suchfeld: Empfänger, Notiz oder Kategorie (gemeinsame Logik mit der Auswertung).
        if (!de.spahr.ausgaben.db.BookingSearch.matches(b, filterPayee)) {
            return false;
        }
        if (!filterCategory.isEmpty() && !categoryMatchesBooking(b)) {
            return false;
        }
        if (filterAmountFrom != null && b.amountCents < filterAmountFrom) {
            return false;
        }
        if (filterAmountTo != null && b.amountCents > filterAmountTo) {
            return false;
        }
        if (filterDateFrom != null && b.createdAt < filterDateFrom) {
            return false;
        }
        return filterDateTo == null || b.createdAt <= filterDateTo;
    }

    /**
     * Bei aktivem Kategorie-Filter zeigt eine Splitbuchung nur den Teilbetrag der gewählten Kategorie;
     * sonst den vollen (vorzeichenbehafteten) Betrag {@code full}. Zusätzlich typgeprüft (Einnahme/
     * Ausgabe, siehe {@link #categoryMatchesBooking}) für gleichnamige Kategorien unterschiedlichen Typs.
     */
    private long displaySignedForFilter(Booking b, long full) {
        if (filterCategory.isEmpty()) {
            return full;
        }
        return de.spahr.ausgaben.db.CategoryBookingFilter.displaySigned(b, splitsByBooking,
                filterCategory, filterCategoryIsMain, full, categoryTypes, filterCategoryIsIncome);
    }

    /**
     * Treffer, wenn die (Haupt-)Kategorie oder eine Teilkategorie einer Splitbuchung passt – zusätzlich
     * typgeprüft ({@link #filterCategoryIsIncome}): kMyMoney erlaubt dieselbe Kategorie-Bezeichnung
     * unabhängig im Einnahme- und im Ausgabe-Baum (z. B. „Versicherung:Krankenzusatz"), maßgeblich ist
     * dabei der Typ der jeweiligen Buchungs-/Split-Zeile selbst (siehe {@link Booking#categoryIsIncome}).
     */
    private boolean categoryMatchesBooking(Booking b) {
        return de.spahr.ausgaben.db.CategoryBookingFilter.matchesBooking(b, splitsByBooking,
                filterCategory, filterCategoryIsMain, categoryTypes, filterCategoryIsIncome);
    }

    private boolean isFilterActive() {
        return !filterPayee.isEmpty() || !filterCategory.isEmpty()
                || filterAmountFrom != null || filterAmountTo != null
                || filterDateFrom != null || filterDateTo != null;
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
        // 2b. Gesamtvermögen (alle Konten + Depotwert), sobald ein Depot importiert wurde.
        if (hasDepot) {
            saldoViews.add(new SaldoView(VIEW_NETWORTH, getString(R.string.saldo_networth),
                    totalBalance + depotValueCents));
        }
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

    /** Summiert den Depotwert über alle Depots (für „Gesamtvermögen") und baut die Saldo-Leiste neu. */
    private void loadDepotTotal(List<String> depots) {
        if (depots == null || depots.isEmpty()) {
            depotValueCents = 0;
            buildSaldoViews();
            showSaldo();
            return;
        }
        final long[] total = {0};
        final int[] pending = {depots.size()};
        for (String depot : depots) {
            repository.getDepotHoldings(depot, holdings -> {
                for (Repository.DepotHolding h : holdings) {
                    total[0] += h.valueCents;
                }
                if (--pending[0] == 0) {
                    depotValueCents = total[0];
                    buildSaldoViews();
                    showSaldo();
                }
            });
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
        return de.spahr.ausgaben.settings.MoneyFormat.display(signedCents,
                de.spahr.ausgaben.settings.Currencies.forAccount(selectedAccount));
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
        // "Alle" (leerer Wert) setzt bewusst keinen Typ – kein Ausschluss.
        final Boolean[] catIsIncome = {filterCategory.isEmpty() ? null : filterCategoryIsIncome};
        CategoryFilterAdapter catAdapter = new CategoryFilterAdapter(this,
                getString(R.string.category_all),
                getString(R.string.category_group_expense), catExpense,
                getString(R.string.category_group_income), catIncome);
        fCategory.setAdapter(catAdapter);
        fCategory.setText(filterCategory, false);
        fCategory.setOnItemClickListener((parent, v, pos, id) -> {
            CategoryFilterAdapter.CatItem it = catAdapter.getItem(pos);
            if (it != null) {
                catValue[0] = it.value;
                catIsMain[0] = it.isMain;
                catIsIncome[0] = it.value.isEmpty() ? null : it.groupIsIncome;
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

        // Datums-Range (Slider in Monatsschritten; taggenau direkt im Feld eingebbar).
        com.google.android.material.slider.RangeSlider dateSlider = view.findViewById(R.id.filterDateSlider);
        TextInputEditText dFrom = view.findViewById(R.id.filterDateFrom);
        TextInputEditText dTo = view.findViewById(R.id.filterDateTo);
        long dtMin = Long.MAX_VALUE;
        long dtMax = Long.MIN_VALUE;
        for (Booking b : allBookings) {
            dtMin = Math.min(dtMin, b.createdAt);
            dtMax = Math.max(dtMax, b.createdAt);
        }
        final MonthRange dateRange;
        if (!allBookings.isEmpty()) {
            dateRange = MonthRange.attach(dateSlider, dFrom, dTo, dtMin, dtMax, filterDateFrom, filterDateTo);
        } else {
            dateRange = null;
            dateSlider.setValueFrom(0f);
            dateSlider.setValueTo(1f);
            dateSlider.setValues(0f, 1f);
            dateSlider.setEnabled(false);
            dFrom.setEnabled(false);
            dTo.setEnabled(false);
        }
        final long dtDataMin = dtMin;
        final long dtDataMax = dtMax;

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.filter_title)
                .setView(view)
                .setPositiveButton(R.string.filter_apply, (d, w) -> {
                    filterPayee = textOf(fPayee).trim();
                    filterCategory = catValue[0] == null ? "" : catValue[0].trim();
                    filterCategoryIsMain = catIsMain[0];
                    filterCategoryIsIncome = filterCategory.isEmpty() ? null : catIsIncome[0];
                    if (dateRange != null) {
                        long df = dateRange.getFromMillis();
                        long dt = dateRange.getToMillis();
                        if (df <= dtDataMin && dt >= dtDataMax) {
                            filterDateFrom = null;
                            filterDateTo = null;
                        } else {
                            filterDateFrom = df;
                            filterDateTo = dt;
                        }
                    } else {
                        filterDateFrom = null;
                        filterDateTo = null;
                    }
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
                    filterCategoryIsIncome = null;
                    filterAmountFrom = null;
                    filterAmountTo = null;
                    filterDateFrom = null;
                    filterDateTo = null;
                    saldoIndex = 0;
                    applyFilter();
                    showSaldo();
                    flashSaldoBar();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String formatCents(long cents) {
        return de.spahr.ausgaben.settings.MoneyFormat.plain(cents);
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
        // Menü-Titel kommen aus dem String-Pool (umgehen die Übersetzung) → per getString neu setzen.
        setMenuTitle(menu, R.id.action_export, R.string.action_export);
        setMenuTitle(menu, R.id.action_filter, R.string.action_filter);
        setMenuTitle(menu, R.id.action_analysis, R.string.action_analysis);
        setMenuTitle(menu, R.id.action_balance, R.string.action_balance);
        setMenuTitle(menu, R.id.action_budget, R.string.action_budget);
        setMenuTitle(menu, R.id.action_scheduled, R.string.action_scheduled);
        setMenuTitle(menu, R.id.action_settings, R.string.action_settings);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // „Geplante Buchungen" nur im KMyMoney-Modus (aus der .kmy importiert).
        android.view.MenuItem scheduled = menu.findItem(R.id.action_scheduled);
        if (scheduled != null) {
            scheduled.setVisible(settings.isKmyMode());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void setMenuTitle(android.view.Menu menu, int itemId, int stringId) {
        android.view.MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setTitle(getString(stringId));
        }
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
            i.putExtra(AnalysisActivity.EXTRA_FILTER_CATEGORY_INCOME,
                    filterCategoryIsIncome == null ? -1 : (filterCategoryIsIncome ? 1 : 0));
            i.putExtra(AnalysisActivity.EXTRA_FILTER_AMOUNT_FROM,
                    filterAmountFrom == null ? Long.MIN_VALUE : filterAmountFrom);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_AMOUNT_TO,
                    filterAmountTo == null ? Long.MAX_VALUE : filterAmountTo);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_DATE_FROM,
                    filterDateFrom == null ? Long.MIN_VALUE : filterDateFrom);
            i.putExtra(AnalysisActivity.EXTRA_FILTER_DATE_TO,
                    filterDateTo == null ? Long.MAX_VALUE : filterDateTo);
            i.putExtra(AnalysisActivity.EXTRA_VIEW_KEY, currentViewKey());
            startActivity(i);
            return true;
        } else if (id == R.id.action_categories) {
            startActivity(new Intent(this, CategoryChartActivity.class));
            return true;
        } else if (id == R.id.action_balance) {
            startActivity(new Intent(this, BalanceActivity.class));
            return true;
        } else if (id == R.id.action_budget) {
            startActivity(new Intent(this, BudgetActivity.class));
            return true;
        } else if (id == R.id.action_scheduled) {
            startActivity(new Intent(this, ScheduledActivity.class));
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
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        if (refreshNeeded) {
                            refreshBookings();
                        }
                    }
                });
    }

    private void runExport() {
        Toast.makeText(this, R.string.export_running, Toast.LENGTH_SHORT).show();
        String tree = settings.hasRemoteConfig() ? null : settings.getLocalExportTree();
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
        if (!settings.hasRemoteConfig()) {
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
        if (!settings.hasRemoteConfig()) {
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
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, getApplicationContext()), getApplicationContext());
                runOnUiThread(() -> {
                    dismissProgress();
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

    /**
     * Auswahl-Dialog mit Mehrfachauswahl: mehrere Konten (und/oder Depots) auf einmal importieren.
     * Bereits importierte Konten (in der App vorhanden) werden ausgeblendet; Depots mit „(Depot)" markiert.
     */
    private void chooseAccountForImport(KmyImporter importer, List<String> accounts, List<String> depots) {
        // Bereits vorhandene App-Konten/Depots ausblenden – nur noch nicht importierte anbieten.
        // Konten inkl. geschlossener (importedAccounts), damit auch geschlossene nicht erneut erscheinen.
        final List<String> newAccounts = new ArrayList<>();
        for (String a : accounts) {
            if (!containsIgnoreCase(importedAccounts, a)) {
                newAccounts.add(a);
            }
        }
        final List<String> newDepots = new ArrayList<>();
        for (String d : depots) {
            if (!containsIgnoreCase(appDepots, d)) {
                newDepots.add(d);
            }
        }
        List<String> labels = new ArrayList<>(newAccounts);
        for (String d : newDepots) {
            labels.add(getString(R.string.kmy_choose_depot, d));
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.kmy_no_new_accounts, Toast.LENGTH_LONG).show();
            return;
        }
        final int accountCount = newAccounts.size();
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
                            accountTargets.add(newAccounts.get(i));
                        } else {
                            depotTargets.add(newDepots.get(i - accountCount));
                        }
                    }
                    if (accountTargets.isEmpty() && depotTargets.isEmpty()) {
                        return; // nichts angehakt
                    }
                    startBatchImport(importer, accountTargets, depotTargets);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Importiert die gewählten Konten als Batch und anschließend die gewählten Depots nacheinander.
     * Läuft komplett im Hintergrund – die Oberfläche bleibt bedienbar; nur bei einem Fehler kommt eine
     * Meldung, am Ende wird die Liste still aktualisiert.
     */
    private void startBatchImport(KmyImporter importer, List<String> accountTargets,
                                  List<String> depotTargets) {
        importStarted();
        // Schritte gesamt: je Konto lesen + Speichern + je Depot.
        final int total = accountTargets.size() + (accountTargets.isEmpty() ? 0 : 1) + depotTargets.size();
        new Thread(() -> {
            try {
                if (accountTargets.isEmpty()) {
                    runOnUiThread(() -> importDepotsThenFinish(importer, depotTargets, 0, total));
                    return;
                }
                // Ein Lesedurchlauf für ALLE Konten (vorher: einer je Konto über die ganze Datei).
                java.util.LinkedHashMap<String, List<Booking>> map = importer.bookingsForAccounts(
                        accountTargets, phaseListener(getString(R.string.import_stage_bookings),
                                de.spahr.ausgaben.export.ImportPhase.BOOKINGS_FROM,
                                de.spahr.ausgaben.export.ImportPhase.BOOKINGS_TO));
                for (String acc : accountTargets) {
                    repository.setAccountCurrency(acc, importer.currencyOf(acc));
                }
                // Konto- und Kategorietypen für ALLE Konten/Kategorien der .kmy übernehmen.
                repository.applyAccountTypes(importer.accountTypes());
                repository.applyCategoryTypes(importer.categoryTypes());
                final int doneAfterSave = accountTargets.size() + 1;
                runOnUiThread(() -> repository.replaceImportAccounts(map,
                        phaseListener(getString(R.string.import_stage_saving),
                                de.spahr.ausgaben.export.ImportPhase.SAVE_FROM,
                                de.spahr.ausgaben.export.ImportPhase.SAVE_TO),
                        res -> importDepotsThenFinish(importer, depotTargets, doneAfterSave, total)));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    /** Importiert die Depots der Reihe nach; am Ende Banner auf 100 % und Liste aktualisieren. */
    private void importDepotsThenFinish(KmyImporter importer, List<String> depots, int done, int total) {
        if (depots.isEmpty()) {
            completeImport();
            return;
        }
        final String depot = depots.get(0);
        final List<String> rest = new ArrayList<>(depots.subList(1, depots.size()));
        setImportProgress(getString(R.string.import_stage_depot, depot), pct(done, total));
        new Thread(() -> {
            try {
                KmyImporter.DepotData data = importer.importDepot(depot);
                repository.replaceDepotImport(depot, data.securities, data.transactions, () ->
                        importDepotsThenFinish(importer, rest, done + 1, total));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    /**
     * Langer Tipp in der Schublade: lädt die .kmy und aktualisiert genau dieses Depot – im Hintergrund
     * mit dem gelben Fortschrittsbanner; die Oberfläche bleibt bedienbar, nur bei Fehlern kommt eine Meldung.
     */
    private void reimportDepot(String depotName) {
        if (!settings.isKmyMode()) {
            Toast.makeText(this, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        if (!settings.hasRemoteConfig()) {
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
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path),
                        phaseListener(getString(R.string.import_stage_download),
                                de.spahr.ausgaben.export.ImportPhase.DOWNLOAD_FROM,
                                de.spahr.ausgaben.export.ImportPhase.DOWNLOAD_TO));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, getApplicationContext(),
                                phaseListener(getString(R.string.import_stage_reading),
                                        de.spahr.ausgaben.export.ImportPhase.READ_FILE_FROM,
                                        de.spahr.ausgaben.export.ImportPhase.READ_FILE_TO)),
                        getApplicationContext());
                postImportProgress(getString(R.string.import_stage_depot, depotName),
                        de.spahr.ausgaben.export.ImportPhase.BOOKINGS_FROM);
                KmyImporter.DepotData data = importer.importDepot(depotName);
                postImportProgress(getString(R.string.import_stage_depot, depotName),
                        de.spahr.ausgaben.export.ImportPhase.SAVE_FROM);
                runOnUiThread(() -> repository.replaceDepotImport(depotName, data.securities,
                        data.transactions, this::completeImport));
            } catch (Exception e) {
                postImportError(e);
            }
        }).start();
    }

    /** Lädt die .kmy und importiert ein Konto ({@code null} = alle bereits vorhandenen App-Konten). */
    private void runKmyImport(final String account) {
        importStarted();
        new Thread(() -> {
            KmyImporter importer;
            try {
                String path = settings.getKmyPath();
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path),
                        phaseListener(getString(R.string.import_stage_download),
                                de.spahr.ausgaben.export.ImportPhase.DOWNLOAD_FROM,
                                de.spahr.ausgaben.export.ImportPhase.DOWNLOAD_TO));
                importer = new KmyImporter(
                        new KmyDocument(raw, getApplicationContext(),
                                phaseListener(getString(R.string.import_stage_reading),
                                        de.spahr.ausgaben.export.ImportPhase.READ_FILE_FROM,
                                        de.spahr.ausgaben.export.ImportPhase.READ_FILE_TO)),
                        getApplicationContext());
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
                    importFinished();
                    Toast.makeText(this, R.string.kmy_account_not_found, Toast.LENGTH_LONG).show();
                });
                return;
            }
            replaceFromImporter(importer, targets);
        }).start();
    }

    /**
     * Baut die Buchungen der Zielkonten und ersetzt sie in der DB. Läuft im Hintergrund; die Liste wird
     * am Ende still aktualisiert, eine Meldung kommt nur bei einem Fehler.
     */
    private void replaceFromImporter(KmyImporter importer, List<String> accounts) {
        try {
            // Ein Lesedurchlauf für ALLE Konten (vorher: einer je Konto über die ganze Datei).
            java.util.LinkedHashMap<String, List<Booking>> map = importer.bookingsForAccounts(accounts,
                    phaseListener(getString(R.string.import_stage_bookings),
                            de.spahr.ausgaben.export.ImportPhase.BOOKINGS_FROM,
                            de.spahr.ausgaben.export.ImportPhase.BOOKINGS_TO));
            for (String acc : accounts) {
                // Währungskennzeichen aus der KMyMoney-Datei je Konto übernehmen.
                repository.setAccountCurrency(acc, importer.currencyOf(acc));
            }
            // Anlage/Verbindlichkeit für ALLE vorhandenen Konten aus der .kmy klassifizieren (nicht nur die neu importierten).
            repository.applyAccountTypes(importer.accountTypes());
            // Kategorietyp (Einnahme/Ausgabe) für ALLE Kategorien der .kmy übernehmen (Budget-Einordnung).
            repository.applyCategoryTypes(importer.categoryTypes());
            // Kein separates „Buchungen werden gespeichert" beim Konto-Aktualisieren – nur die Konto-Phase zeigen.
            runOnUiThread(() -> repository.replaceImportAccounts(map,
                    phaseListener(getString(R.string.import_running_banner),
                            de.spahr.ausgaben.export.ImportPhase.SAVE_FROM,
                            de.spahr.ausgaben.export.ImportPhase.SAVE_TO),
                    res -> completeImport()));
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
            importFinished();
            Toast.makeText(this, getString(R.string.import_failed, msg), Toast.LENGTH_LONG).show();
        });
    }

    /** Zeigt den gelben „Konto wird importiert"-Banner (zählt laufende Hintergrund-Importe). */
    private void importStarted() {
        activeImports++;
        if (importBanner != null) {
            importBanner.setVisibility(View.VISIBLE);
            importShimmer.start();
            setImportProgress(getString(R.string.import_running_banner), 0);
        }
    }

    /** Blendet den Banner aus, sobald kein Import mehr läuft. */
    private void importFinished() {
        activeImports = Math.max(0, activeImports - 1);
        if (importBanner != null && activeImports == 0) {
            importShimmer.stop();
            importBanner.setVisibility(View.GONE);
        }
    }

    /** Status-Text und Prozentanzeige im Banner setzen (Main-Thread). */
    private void setImportProgress(String label, int percent) {
        if (importStatus != null) {
            importStatus.setText(label);
        }
        if (importPercent != null) {
            importPercent.setText(Math.max(0, Math.min(100, percent)) + " %");
        }
    }

    /** Fortschritt aus einem Hintergrund-Thread melden. */
    private void postImportProgress(String label, int percent) {
        runOnUiThread(() -> setImportProgress(label, percent));
    }

    private static int pct(int done, int total) {
        return total <= 0 ? 100 : Math.round(100f * done / total);
    }

    /** Zuletzt gemeldeter Prozentwert – gegen Fluten des Main-Threads (der Download meldet je 8 KB). */
    private int lastPostedPercent = -1;

    /**
     * Fortschritts-Empfänger für eine Phase: bildet {@code done/total} auf {@code from..to} ab und meldet
     * nur, wenn sich der ganzzahlige Prozentwert geändert hat.
     */
    private de.spahr.ausgaben.util.ProgressListener phaseListener(String label, int from, int to) {
        return (done, total) -> {
            int p = de.spahr.ausgaben.export.ImportPhase.map(done, total, from, to);
            if (p != lastPostedPercent) {
                lastPostedPercent = p;
                postImportProgress(label, p);
            }
        };
    }

    /** Import abgeschlossen: 100 % kurz zeigen, dann Banner ausblenden und Liste aktualisieren. */
    private void completeImport() {
        setImportProgress(getString(R.string.import_stage_done), 100);
        importBanner.postDelayed(this::importFinished, 600);
        refreshBookings();
    }

    // ---- CSV-Import (Nextcloud-Liste oder lokaler Picker) ----

    private void startCsvImport() {
        if (settings.hasRemoteConfig()) {
            browseCsvAt(settings.getImportFolder());
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

    /** Übergeordneter Ordner („a/b/c" → „a/b", „a" → „"). */
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

    /** Navigierbarer CSV-Browser (Unterordner + CSV-Dateien) im entfernten Importordner. */
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
        final List<String> labels = new java.util.ArrayList<>();
        final List<Runnable> actions = new java.util.ArrayList<>();
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
            actions.add(() -> downloadAndImport(folder, f));
        }
        String title = folder.isEmpty() ? getString(R.string.choose_import_file) : "/" + folder;
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadAndImport(String folder, String fileName) {
        new Thread(() -> {
            try {
                String content = RemoteStorage.from(settings).downloadText(folder, fileName);
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
            CsvImporter importer = new CsvImporter(this);
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
