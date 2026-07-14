package de.spahr.ausgaben.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;
import de.spahr.ausgaben.db.PayeeCorrection;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.location.LocationTagger;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Vereinheitlichter Editor für Neueingabe und Bearbeitung.
 * Unterstützt drei Typen: Ausgabe / Umbuchung / Einnahme. Bei Ausgabe/Einnahme können mehrere
 * Kategorien mit Teilbeträgen erfasst werden (Splitbuchung); bei Umbuchung zwei Konten (Von/Nach).
 */
public class BookingEditActivity extends LocalizedActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";
    /** Öffnet den Editor als NEUE Buchung, vorbefüllt aus dieser Vorlage-Buchung (Sprach-Schnellerfassung). */
    public static final String EXTRA_TEMPLATE_BOOKING_ID = "template_booking_id";
    /** Gesprochener Betrag in Cent (−1 = keiner). */
    public static final String EXTRA_VOICE_AMOUNT_CENTS = "voice_amount_cents";
    /** Vorbelegter Empfänger (falls keine Vorlage gefunden wurde). */
    public static final String EXTRA_PREFILL_PAYEE = "prefill_payee";
    /** Ursprünglich gesprochener Empfänger – zum Anbieten einer Namenskorrektur beim Speichern. */
    public static final String EXTRA_VOICE_SPOKEN_PAYEE = "voice_spoken_payee";
    /** Passender Alias (ID) für eine neue Sprachbuchung – füllt Konto/Kategorien/Von-Bis vor. */
    public static final String EXTRA_ALIAS_ID = "alias_id";
    /** Neue Buchung mit vorbelegtem Konto (aus der Ort-Ansicht der Bestände). */
    public static final String EXTRA_PRESET_ACCOUNT = "preset_account";
    /** Neue Buchung mit vorbelegtem Ort (aus der Ort-Ansicht der Bestände; leer = „ohne Ort"). */
    public static final String EXTRA_PRESET_PLACE = "preset_place";
    /** Öffnet eine bestehende Buchung nur zur Ansicht (keine Änderung möglich). */
    public static final String EXTRA_READ_ONLY = "read_only";
    /** Öffnet eine geplante Buchung ({@link de.spahr.ausgaben.db.ScheduledTransaction}) nur zur Ansicht. */
    public static final String EXTRA_SCHEDULED_ID = "scheduled_id";
    /** Fälligkeitstermin (ms) der getippten Planung – als Buchungsdatum in der Vorschau. */
    public static final String EXTRA_SCHEDULED_DUE_MS = "scheduled_due_ms";

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;
    private Booking booking; // null = Neu-Modus
    /** true = reine Ansicht (kurzer Druck): alle Felder gesperrt, keine Aktionsknöpfe. */
    private boolean readOnly;

    // Ursprünglicher Typ beim Bearbeiten (für Umbuchung ↔ normale Buchung Umwandlungen).
    private boolean origIsTransfer;
    private String origTransferGroup = "";
    // True, wenn die bearbeitete Buchung in der App angelegt (ort-verknüpft) ist – nur dann Ort-Feld zeigen.
    private boolean origPlaceManaged;
    // Für die Datum-Abfrage: wurde der Editor aus einer bestehenden Buchung geöffnet, und hat der Nutzer
    // das Datum selbst geändert? Abfrage nur beim Kopieren (Vorlage) mit unverändertem Datum.
    private boolean openedFromExistingBooking;
    private boolean dateChangedByUser;

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleType;
    private android.widget.TextView typeHeading;
    private android.widget.TextView textBalanceBefore;
    private android.widget.TextView textBalanceAfter;
    private android.widget.ImageButton btnNoteMap;
    private TextInputEditText editAmount;
    private TextInputLayout payeeLayout;
    private MaterialAutoCompleteTextView editPayee;
    private TextInputLayout accountLayout;
    private MaterialAutoCompleteTextView editAccount;
    private TextInputLayout accountToLayout;
    private MaterialAutoCompleteTextView editAccountTo;
    private TextInputLayout placeLayout;
    private MaterialAutoCompleteTextView editPlace;
    private TextInputLayout placeToLayout;
    private MaterialAutoCompleteTextView editPlaceTo;
    private View splitSection;
    private android.widget.LinearLayout splitContainer;
    private TextInputEditText editNote;
    private TextInputEditText editDate;
    private com.google.android.material.materialswitch.MaterialSwitch switchExported;
    private MaterialButton btnToday;
    private MaterialButton btnSaveNew;
    private MaterialButton btnUpdate;
    private MaterialButton btnDelete;

    /** GPS-Anhang an die Notiz – nur im Neu-Modus aktiv (bei bestehenden Buchungen bleibt der Ort unberührt). */
    private LocationTagger locationTagger;
    private ActivityResultLauncher<String> locationPermissionLauncher;

    /** Ursprünglich gesprochener Empfänger (aus der Sprach-Erfassung) – für die Korrektur-Nachfrage. */
    private String voiceSpokenPayee;
    /** Ursprünglicher Empfänger beim Bearbeiten – „Von"-Name für die Alias-Nachfrage. */
    private String origPayee;
    /** Vorbelegter Empfänger (Prefill/Alias/geladene Buchung) – Abfrage nur bei manueller Änderung. */
    private String prefilledPayee;
    /** Passender Alias für die Vorbelegung einer neuen Sprachbuchung (null = keiner). */
    private PayeeCorrection activeAlias;

    /** Verwaltet die dynamische Kategorie-/Teilbetrag-Liste (Splitbuchung). */
    private SplitRowController splitCtl;

    private final SimpleDateFormat dateDisplay = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final Calendar selectedDate = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_booking);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        settings = new SettingsStore(this);
        placesStore = new PlacesStore(this);

        toggleType = findViewById(R.id.toggleType);
        typeHeading = findViewById(R.id.typeHeading);
        textBalanceBefore = findViewById(R.id.textBalanceBefore);
        textBalanceAfter = findViewById(R.id.textBalanceAfter);
        btnNoteMap = findViewById(R.id.btnNoteMap);
        editAmount = findViewById(R.id.editAmount);
        editAmount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        payeeLayout = findViewById(R.id.payeeLayout);
        editPayee = findViewById(R.id.editPayee);
        accountLayout = findViewById(R.id.accountLayout);
        editAccount = findViewById(R.id.editAccount);
        accountToLayout = findViewById(R.id.accountToLayout);
        editAccountTo = findViewById(R.id.editAccountTo);
        placeLayout = findViewById(R.id.placeLayout);
        editPlace = findViewById(R.id.editPlace);
        placeToLayout = findViewById(R.id.placeToLayout);
        editPlaceTo = findViewById(R.id.editPlaceTo);
        splitSection = findViewById(R.id.splitSection);
        splitContainer = findViewById(R.id.splitContainer);
        readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);
        splitCtl = new SplitRowController(splitContainer, editAmount, getLayoutInflater(),
                readOnly, this::updateSaveEnabled);
        editNote = findViewById(R.id.editNote);
        editDate = findViewById(R.id.editDate);
        switchExported = findViewById(R.id.switchExported);
        editDate.setOnClickListener(v -> showDatePicker());

        btnToday = findViewById(R.id.btnToday);
        btnToday.setOnClickListener(v -> {
            selectedDate.setTime(new java.util.Date());
            dateChangedByUser = true;
            updateDateField();
        });

        btnSaveNew = findViewById(R.id.btnSaveNew);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnDelete = findViewById(R.id.btnDelete);

        repository.getPayeeNames(names -> editPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> {
            editAccount.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            editAccountTo.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        });
        repository.getCategoriesGrouped(g -> {
            // Kategoriefeld nach Ausgabe/Einnahme gruppiert (Überschriften), ohne „alle"-Eintrag.
            splitCtl.setAdapter(new CategoryFilterAdapter(this, null,
                    getString(R.string.category_group_expense), g.expense,
                    getString(R.string.category_group_income), g.income));
        });

        // Ort-Dropdown folgt dem gewählten Konto: bei Ausgabe/Einnahme der Ort, bei Umbuchung der Von-Ort.
        // Danach die Sichtbarkeit aktualisieren (Ortsfeld nur bei Konten mit Orten).
        editAccount.setOnItemClickListener((parent, view, position, id) -> {
            if (isTransferType()) {
                setupPlaceOptions(editPlace, textOf(editAccount).trim(), false);
            } else {
                setupPlaceDropdown(textOf(editAccount).trim());
            }
            applyTypeVisibility();
        });
        // Bei einer Umbuchung folgt der Nach-Ort dem Nach-Konto.
        editAccountTo.setOnItemClickListener((parent, view, position, id) -> {
            if (isTransferType()) {
                setupPlaceOptions(editPlaceTo, textOf(editAccountTo).trim(), false);
            }
            applyTypeVisibility();
        });

        // Gesamtbetrag ↔ Teilbeträge koppeln; Konto wirkt auf die Freischaltung der Buttons.
        editAmount.addTextChangedListener(new SimpleWatcher(splitCtl::onTotalChanged));
        editAccount.addTextChangedListener(new SimpleWatcher(this::updateSaveEnabled));
        editAccountTo.addTextChangedListener(new SimpleWatcher(this::updateSaveEnabled));

        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyTypeVisibility();
                applyAlias();
            }
        });

        btnSaveNew.setOnClickListener(v -> saveAsNew());
        btnUpdate.setOnClickListener(v -> update());
        btnDelete.setOnClickListener(v -> confirmDelete());

        long templateId = getIntent().getLongExtra(EXTRA_TEMPLATE_BOOKING_ID, -1);
        long id = getIntent().getLongExtra(EXTRA_BOOKING_ID, -1);
        long scheduledId = getIntent().getLongExtra(EXTRA_SCHEDULED_ID, -1);
        long voiceAmount = getIntent().getLongExtra(EXTRA_VOICE_AMOUNT_CENTS, -1);
        voiceSpokenPayee = getIntent().getStringExtra(EXTRA_VOICE_SPOKEN_PAYEE);

        // Nur bei NEUEN Buchungen und aktivem GPS: Standort vorwärmen und ggf. Berechtigung anfragen,
        // damit beim Speichern Koordinaten an die Notiz angehängt werden können.
        if (id < 0 && scheduledId < 0 && settings.isGpsEnabled()) {
            locationTagger = new LocationTagger(this);
            locationTagger.setOnLocationUpdate(this::refreshNoteLocation);
            locationPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), granted -> {
                        if (granted) {
                            locationTagger.start();
                            refreshNoteLocation();
                        }
                    });
            if (hasLocationPermission()) {
                locationTagger.start();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (scheduledId >= 0) {
            // Geplante Buchung nur zur Ansicht (1:1 wie eine normale Buchung).
            readOnly = true;
            long dueMs = getIntent().getLongExtra(EXTRA_SCHEDULED_DUE_MS, System.currentTimeMillis());
            repository.getScheduledById(scheduledId, st -> bindScheduledPreview(st, dueMs));
        } else if (templateId >= 0) {
            // Sprach-Schnellerfassung: neue Buchung aus Vorlage vorbefüllen.
            final Long amount = voiceAmount >= 0 ? voiceAmount : null;
            repository.getBookingById(templateId, b -> bindTemplate(b, amount));
        } else if (id >= 0) {
            repository.getBookingById(id, this::bindEditMode);
        } else {
            setupNewMode();
            // Vorbelegtes Konto+Ort (aus der Ort-Ansicht der Bestände).
            String presetAccount = getIntent().getStringExtra(EXTRA_PRESET_ACCOUNT);
            if (presetAccount != null && !presetAccount.isEmpty()) {
                editAccount.setText(presetAccount, false);
                setupPlaceDropdown(presetAccount);
                String presetPlace = getIntent().getStringExtra(EXTRA_PRESET_PLACE);
                editPlace.setText(presetPlace == null || presetPlace.isEmpty()
                        ? PlacesStore.NO_PLACE : presetPlace, false);
            }
            // Fallback der Sprach-Erfassung (keine Vorlage gefunden): Empfänger/Betrag vorbelegen.
            String prefillPayee = getIntent().getStringExtra(EXTRA_PREFILL_PAYEE);
            if (prefillPayee != null && !prefillPayee.isEmpty()) {
                editPayee.setText(prefillPayee);
            }
            prefilledPayee = prefillPayee == null ? "" : prefillPayee;
            if (voiceAmount >= 0) {
                editAmount.setText(formatCents(voiceAmount));
            }
            // Alias-Treffer: bevorzugte Buchungsart setzen und Konto/Kategorien/Von-Bis vorbelegen.
            long aliasId = getIntent().getLongExtra(EXTRA_ALIAS_ID, -1);
            if (aliasId >= 0) {
                repository.getAlias(aliasId, a -> {
                    activeAlias = a;
                    if (a != null) {
                        toggleType.check(aliasTypeButton(a.type));
                    }
                    applyAlias();
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationTagger != null && hasLocationPermission()) {
            locationTagger.start();
            refreshNoteLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationTagger != null) {
            locationTagger.stop();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Zeigt die aktuellen Koordinaten als „GPS: lat, lon" bereits im Notizfeld an (nur Neu-Modus). Ein
     * vorhandener GPS-Zusatz wird durch den frischeren ersetzt, der übrige Notiztext bleibt erhalten.
     * Während der Nutzer im Feld tippt (Fokus), wird nicht überschrieben; ohne Standort passiert nichts.
     */
    private void refreshNoteLocation() {
        if (locationTagger == null || editNote == null || editNote.hasFocus()) {
            return;
        }
        String coords = locationTagger.currentCoordinates();
        if (coords == null) {
            return;
        }
        String current = textOf(editNote);
        String base = current.replaceAll("\\s*GPS:.*$", "").replaceAll("\\s+$", "");
        String updated = base.isEmpty() ? "GPS: " + coords : base + " GPS: " + coords;
        if (!updated.equals(current)) {
            editNote.setText(updated);
        }
    }

    /**
     * Bietet an, einen Alias zu lernen, falls der Empfänger gegenüber dem Ausgangs-Namen geändert wurde –
     * bei einer Sprach-Neubuchung der gesprochene Begriff ({@code voiceSpokenPayee}), beim Bearbeiten der
     * ursprüngliche Empfänger ({@code origPayee}). Gesteuert über den Einstellungs-Schalter. Der Alias
     * übernimmt den aktuellen Buchungskontext (Konto, Kategorien bzw. Von/Bis). Danach {@code proceed}.
     */
    private void maybeAskCorrection(String finalPayee, Runnable proceed) {
        String spoken = voiceSpokenPayee != null && !voiceSpokenPayee.trim().isEmpty()
                ? voiceSpokenPayee.trim()
                : (origPayee == null ? "" : origPayee.trim());
        String corrected = finalPayee == null ? "" : finalPayee.trim();
        String prefilled = prefilledPayee == null ? "" : prefilledPayee.trim();
        // Nur fragen, wenn der Empfänger gegenüber der Vorbelegung manuell geändert wurde.
        if (!settings.isAliasPromptEnabled() || spoken.isEmpty() || corrected.isEmpty()
                || corrected.equalsIgnoreCase(prefilled) || spoken.equalsIgnoreCase(corrected)) {
            proceed.run();
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.correction_title)
                .setMessage(getString(R.string.correction_message, spoken, corrected))
                .setCancelable(false)
                .setPositiveButton(R.string.correction_save, (d, w) -> {
                    // Lernen: die Buchungsposition an die GPS-Liste des Alias anhängen (nicht ersetzen).
                    repository.saveAlias(buildAliasFromForm(spoken, corrected), true);
                    proceed.run();
                })
                .setNegativeButton(R.string.correction_discard, (d, w) -> proceed.run())
                .show();
    }

    /** Zugehöriger Typ-Knopf zur Alias-Buchungsart (Standard: Ausgabe). */
    private int aliasTypeButton(String type) {
        if (Repository.VOICE_TYPE_TRANSFER.equals(type)) {
            return R.id.btnTransfer;
        }
        if (Repository.VOICE_TYPE_INCOME.equals(type)) {
            return R.id.btnIncome;
        }
        return R.id.btnExpense;
    }

    private String currentTypeConstant() {
        if (isTransferType()) {
            return Repository.VOICE_TYPE_TRANSFER;
        }
        return toggleType.getCheckedButtonId() == R.id.btnIncome
                ? Repository.VOICE_TYPE_INCOME : Repository.VOICE_TYPE_EXPENSE;
    }

    /** Baut aus dem aktuellen Formular einen Alias mit passendem Kontext (Konto/Kategorien bzw. Von/Bis). */
    private PayeeCorrection buildAliasFromForm(String spoken, String corrected) {
        PayeeCorrection a = new PayeeCorrection();
        a.spoken = spoken;
        a.corrected = corrected;
        a.type = currentTypeConstant();
        if (isTransferType()) {
            a.fromAccount = textOf(editAccount).trim();
            a.toAccount = textOf(editAccountTo).trim();
            a.fromPlace = selectedPlace();
            a.toPlace = selectedPlaceTo();
        } else {
            a.account = textOf(editAccount).trim();
            a.place = selectedPlace();
            List<SplitRowController.Part> parts = splitCtl.collectParts();
            String c1 = parts.size() > 0 ? parts.get(0).category : "";
            String c2 = parts.size() > 1 ? parts.get(1).category : "";
            if (toggleType.getCheckedButtonId() == R.id.btnIncome) {
                a.catIncome1 = c1;
                a.catIncome2 = c2;
            } else {
                a.catExpense1 = c1;
                a.catExpense2 = c2;
            }
        }
        // Standort der Buchung (aus der Notiz) übernehmen → Alias per GPS auffindbar (Betrag-only).
        double[] ll = de.spahr.ausgaben.location.Geo.parse(textOf(editNote));
        if (ll != null) {
            a.lat = ll[0];
            a.lon = ll[1];
        }
        return a;
    }

    /** Belegt bei einer neuen Sprachbuchung mit Alias-Treffer die Felder für den aktuellen Typ vor. */
    private void applyAlias() {
        if (activeAlias == null || booking != null) {
            return;
        }
        if (isTransferType()) {
            if (!activeAlias.fromAccount.isEmpty()) {
                editAccount.setText(activeAlias.fromAccount, false);
            }
            if (!activeAlias.toAccount.isEmpty()) {
                editAccountTo.setText(activeAlias.toAccount, false);
            }
            // Ort-Dropdowns/Sichtbarkeit für die neuen Konten aufbauen, dann Alias-Orte vorbelegen.
            applyTypeVisibility();
            if (!activeAlias.fromPlace.isEmpty()) {
                editPlace.setText(activeAlias.fromPlace, false);
            }
            if (!activeAlias.toPlace.isEmpty()) {
                editPlaceTo.setText(activeAlias.toPlace, false);
            }
            return;
        }
        if (!activeAlias.account.isEmpty()) {
            editAccount.setText(activeAlias.account, false);
            setupPlaceDropdown(activeAlias.account);
            if (!activeAlias.place.isEmpty()) {
                editPlace.setText(activeAlias.place, false);
            }
        }
        boolean income = toggleType.getCheckedButtonId() == R.id.btnIncome;
        String c1 = income ? activeAlias.catIncome1 : activeAlias.catExpense1;
        String c2 = income ? activeAlias.catIncome2 : activeAlias.catExpense2;
        splitCtl.setSuppressEvents(true);
        splitCtl.clear();
        if (c1 != null && !c1.trim().isEmpty()) {
            splitCtl.addRow(c1, null);
        }
        if (c2 != null && !c2.trim().isEmpty()) {
            splitCtl.addRow(c2, null);
        }
        splitCtl.setSuppressEvents(false);
        splitCtl.ensureTrailingRow();
        // Ortsfeld-Sichtbarkeit an das vom Alias gesetzte Konto anpassen.
        applyTypeVisibility();
        updateSaveEnabled();
    }

    private void setupNewMode() {
        booking = null;
        origIsTransfer = false;
        origTransferGroup = "";
        origPlaceManaged = true; // neue Buchung ist immer ort-verknüpft (Standardort)
        openedFromExistingBooking = false;
        toolbar.setTitle(R.string.new_booking_title);
        toggleType.check(R.id.btnExpense);
        selectedDate.setTime(new java.util.Date());
        updateDateField();
        String def = settings.getDefaultAccount();
        if (!def.isEmpty()) {
            editAccount.setText(def, false);
        }
        setupPlaceDropdown(def);
        splitCtl.clear();
        splitCtl.ensureTrailingRow();
        switchExported.setVisibility(View.GONE);
        btnUpdate.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        applyTypeVisibility();
        updateSaveEnabled();
        refreshNoteLocation();
    }

    private void bindEditMode(Booking b) {
        if (b == null) {
            finish();
            return;
        }
        booking = b;
        origIsTransfer = b.isTransfer;
        origTransferGroup = b.transferGroup == null ? "" : b.transferGroup;
        origPlaceManaged = b.placeManaged; // importierte Buchungen (false) bekommen kein Ort-Feld
        openedFromExistingBooking = true; // aus bestehender Buchung → Datum-Abfrage nur beim Kopieren
        origPayee = b.payee;
        prefilledPayee = b.payee;
        toolbar.setTitle(R.string.edit_title);
        selectedDate.setTimeInMillis(b.createdAt);
        updateDateField();
        switchExported.setVisibility(b.isTransfer ? View.GONE : View.VISIBLE);
        switchExported.setChecked(b.exported);
        btnUpdate.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        populateFrom(b, null);
        if (readOnly) {
            applyReadOnly();
        }
    }

    /**
     * Zeigt eine geplante Buchung 1:1 wie eine normale Read-Only-Buchung: baut ein synthetisches
     * {@link Booking} aus der Planung (inkl. Split-Kategorien) und schickt es durch denselben
     * {@code populateFrom}+{@code applyReadOnly}-Pfad wie {@link #bindEditMode}.
     */
    private void bindScheduledPreview(de.spahr.ausgaben.db.ScheduledTransaction st, long dueMs) {
        if (st == null) {
            finish();
            return;
        }
        final Booking b = new Booking();
        b.id = -1;
        b.createdAt = dueMs;
        b.amountCents = st.amountCents;
        b.isIncome = st.kind == de.spahr.ausgaben.db.ScheduledTransaction.KIND_INCOME;
        b.isTransfer = st.kind == de.spahr.ausgaben.db.ScheduledTransaction.KIND_TRANSFER;
        b.account = st.account;
        b.payee = st.payee;
        b.note = "";
        b.place = "";
        b.placeManaged = false;
        if (b.isTransfer) {
            b.transferAccount = st.counterparty;
            b.category = "";
        } else {
            b.category = st.counterparty;
        }
        booking = b;
        origIsTransfer = b.isTransfer;
        origPlaceManaged = false;
        openedFromExistingBooking = true;
        selectedDate.setTimeInMillis(dueMs);
        updateDateField();
        if (st.split == 1) {
            repository.getScheduledSplits(st.id, parts -> {
                b.parts = new ArrayList<>();
                if (parts != null) {
                    for (de.spahr.ausgaben.db.ScheduledSplit p : parts) {
                        b.parts.add(new BookingSplit(0, p.category, p.amountCents));
                    }
                }
                populateFrom(b, null);
                applyReadOnly();
            });
        } else {
            populateFrom(b, null);
            applyReadOnly();
        }
    }

    /** Reine Ansicht: Titel setzen, alle Felder sperren, Aktionsknöpfe ausblenden. */
    private void applyReadOnly() {
        toolbar.setTitle(R.string.booking_view_title);
        lockField(editAmount);
        lockField(editPayee);
        lockField(editAccount);
        lockField(editAccountTo);
        lockField(editPlace);
        lockField(editPlaceTo);
        lockField(editNote);
        lockField(editDate);
        // Dropdown-Pfeile (Exposed-Menü) entfernen, damit sich keine Auswahl öffnen lässt.
        accountLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        accountToLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        placeLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        placeToLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        // Ansicht: „Als exportiert markiert" ausblenden (im Bearbeiten-Modus bleibt der Schalter).
        switchExported.setVisibility(View.GONE);
        btnToday.setVisibility(View.GONE);
        btnSaveNew.setVisibility(View.GONE);
        btnUpdate.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);

        // Umschaltknöpfe durch eine große farbige Typ-Überschrift ersetzen
        // (Einnahme = grün, Umbuchung = gelb, Ausgabe = rot).
        toggleType.setVisibility(View.GONE);
        int typeRes;
        int typeColor;
        if (booking.isTransfer) {
            typeRes = R.string.type_transfer;
            typeColor = R.color.transfer_yellow;
        } else if (booking.isIncome) {
            typeRes = R.string.type_income;
            typeColor = R.color.income_green;
        } else {
            typeRes = R.string.type_expense;
            typeColor = R.color.expense_red;
        }
        typeHeading.setText(typeRes);
        typeHeading.setTextColor(getColor(typeColor));
        typeHeading.setVisibility(View.VISIBLE);

        // Kontostand vor/nach dieser Buchung auf dem Konto der Buchung.
        showBalances();

        // Ortssymbol, wenn die Notiz GPS-Koordinaten enthält → Karte mit der Position öffnen.
        final double[] coords = de.spahr.ausgaben.location.Geo.parse(booking.note);
        if (coords != null) {
            btnNoteMap.setVisibility(View.VISIBLE);
            btnNoteMap.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(this, MapPickerActivity.class);
                i.putExtra(MapPickerActivity.EXTRA_LAT, coords[0]);
                i.putExtra(MapPickerActivity.EXTRA_LON, coords[1]);
                i.putExtra(MapPickerActivity.EXTRA_VIEW_ONLY, true);
                startActivity(i);
            });
        } else {
            btnNoteMap.setVisibility(View.GONE);
        }
    }

    /** Zeigt „Kontostand vor/nach der Buchung" für das Konto dieser Buchung. */
    private void showBalances() {
        final long signed = booking.isIncome ? booking.amountCents : -booking.amountCents;
        final String currency = de.spahr.ausgaben.settings.Currencies.forAccount(booking.account);
        repository.getAccountBalanceUpTo(booking.account, booking.createdAt, booking.id, after -> {
            long before = after - signed;
            textBalanceBefore.setText(getString(R.string.balance_before,
                    de.spahr.ausgaben.settings.MoneyFormat.display(before, currency)));
            textBalanceAfter.setText(getString(R.string.balance_after,
                    de.spahr.ausgaben.settings.MoneyFormat.display(after, currency)));
            textBalanceBefore.setVisibility(View.VISIBLE);
            textBalanceAfter.setVisibility(View.VISIBLE);
        });
    }

    /** Macht ein Eingabefeld nicht editierbar, aber lesbar (kein Fokus/Cursor/Dropdown/Tastatur). */
    private void lockField(android.widget.EditText e) {
        e.setFocusable(false);
        e.setFocusableInTouchMode(false);
        e.setClickable(false);
        e.setLongClickable(false);
        e.setCursorVisible(false);
        e.setKeyListener(null);
        e.setOnClickListener(null);
    }

    /**
     * Öffnet als NEUE Buchung, vorbefüllt aus der Vorlage {@code b} (Sprach-Schnellerfassung): heutiges
     * Datum + {@code amountCents} (falls gesetzt), alle übrigen Daten aus der Vorlage.
     */
    private void bindTemplate(Booking b, Long amountCents) {
        if (b == null) {
            setupNewMode();
            return;
        }
        booking = null; // Neu-Modus → Speichern legt eine neue Buchung an
        origIsTransfer = false;
        origTransferGroup = "";
        origPlaceManaged = true;
        openedFromExistingBooking = true; // Vorlage aus bestehender Buchung → Datum-Abfrage möglich
        toolbar.setTitle(R.string.new_booking_title);
        selectedDate.setTime(new java.util.Date());
        updateDateField();
        switchExported.setVisibility(View.GONE);
        btnUpdate.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        populateFrom(b, amountCents);
        refreshNoteLocation();
    }

    /**
     * Füllt die Felder aus {@code b}. {@code overrideAmountCents} (nicht null) ersetzt den Gesamtbetrag;
     * Splitbuchungen werden dann proportional skaliert (letzte Zeile nimmt den Rundungsrest).
     */
    private void populateFrom(Booking b, Long overrideAmountCents) {
        final long total = overrideAmountCents != null ? overrideAmountCents : b.amountCents;
        editNote.setText(b.note);

        if (b.isTransfer) {
            toggleType.check(R.id.btnTransfer);
            editPayee.setText(b.payee);
            // Einnahme = Geld kam auf dieses Konto → dieses Konto ist „Nach".
            if (b.isIncome) {
                editAccount.setText(b.transferAccount, false);
                editAccountTo.setText(b.account, false);
            } else {
                editAccount.setText(b.account, false);
                editAccountTo.setText(b.transferAccount, false);
            }
            editAmount.setText(formatCents(total));
            applyTypeVisibility();
            // Von-/Nach-Ort aus beiden Seiten der Umbuchung vorbelegen.
            if (b.transferGroup != null && !b.transferGroup.isEmpty()) {
                repository.getTransferGroup(b.transferGroup, pair -> {
                    for (Booking side : pair) {
                        String pl = (side.place == null || side.place.isEmpty())
                                ? PlacesStore.NO_PLACE : side.place;
                        if (side.isIncome) {
                            setupPlaceOptions(editPlaceTo, textOf(editAccountTo).trim(), false);
                            editPlaceTo.setText(pl, false);
                        } else {
                            setupPlaceOptions(editPlace, textOf(editAccount).trim(), false);
                            editPlace.setText(pl, false);
                        }
                    }
                });
            }
            updateSaveEnabled();
            return;
        }

        toggleType.check(b.isIncome ? R.id.btnIncome : R.id.btnExpense);
        editPayee.setText(b.payee);
        editAccount.setText(b.account, false);
        setupPlaceDropdown(b.account);
        // Gespeicherten Ort der Buchung vorbelegen (leer → „ohne Ort").
        editPlace.setText(b.place == null || b.place.isEmpty() ? PlacesStore.NO_PLACE : b.place, false);
        applyTypeVisibility();

        final long templateAmount = b.amountCents;
        final String singleCategory = b.category;
        // Geplante Vorschau: Split-Teile liegen direkt an {@code b.parts} (keine DB-Buchung vorhanden).
        if (b.parts != null) {
            fillSplitRows(b.parts, total, templateAmount, singleCategory);
            return;
        }
        // Kategorie-Teile laden (oder Einzelkategorie als eine Zeile); Betrag ggf. skaliert übernehmen.
        repository.getSplits(b.id, splits -> fillSplitRows(splits, total, templateAmount, singleCategory));
    }

    /** Füllt die Split-Zeilen aus {@code splits} (proportional auf {@code total} skaliert). */
    private void fillSplitRows(List<BookingSplit> splits, long total, long templateAmount,
                               String singleCategory) {
        splitCtl.setSuppressEvents(true);
        splitCtl.clear();
        if (splits != null && !splits.isEmpty()) {
            long assigned = 0;
            for (int idx = 0; idx < splits.size(); idx++) {
                BookingSplit s = splits.get(idx);
                long part;
                if (idx < splits.size() - 1 && templateAmount != 0) {
                    part = Math.round((double) s.amountCents * total / templateAmount);
                    assigned += part;
                } else {
                    part = total - assigned; // letzte Zeile → exakte Summe = Gesamtbetrag
                }
                splitCtl.addRow(s.category, formatCents(part));
            }
        } else if (!singleCategory.isEmpty()) {
            splitCtl.addRow(singleCategory, formatCents(total));
        }
        splitCtl.setSuppressEvents(false);
        splitCtl.ensureTrailingRow();
        editAmount.setText(formatCents(total));
        updateSaveEnabled();
    }

    private boolean isTransferType() {
        return toggleType.getCheckedButtonId() == R.id.btnTransfer;
    }

    /** Blendet Felder je nach Typ ein/aus (Umbuchung: zwei Konten, keine Kategorie/Ort/Empfänger). */
    private void applyTypeVisibility() {
        boolean transfer = isTransferType();
        accountToLayout.setVisibility(transfer ? View.VISIBLE : View.GONE);
        // Empfänger gibt es auch bei einer Umbuchung („Zahlungsempfänger"); Kategorien nicht.
        payeeLayout.setVisibility(View.VISIBLE);
        if (transfer) {
            // Umbuchung: Von-/Nach-Ort nur, wenn das jeweilige Konto Orte hat; Dropdowns folgen dem Konto.
            placeLayout.setHint(getString(R.string.transfer_place_from));
            setupPlaceOptions(editPlace, textOf(editAccount).trim(), true);
            setupPlaceOptions(editPlaceTo, textOf(editAccountTo).trim(), true);
            placeLayout.setVisibility(hasPlaces(textOf(editAccount)) ? View.VISIBLE : View.GONE);
            placeToLayout.setVisibility(hasPlaces(textOf(editAccountTo)) ? View.VISIBLE : View.GONE);
        } else {
            // Ort-Feld nur bei in der App angelegten (ort-verknüpften) Buchungen UND wenn das Konto Orte hat.
            placeLayout.setHint(getString(R.string.place_hint));
            placeLayout.setVisibility(
                    origPlaceManaged && hasPlaces(textOf(editAccount)) ? View.VISIBLE : View.GONE);
            placeToLayout.setVisibility(View.GONE);
        }
        splitSection.setVisibility(transfer ? View.GONE : View.VISIBLE);
        accountLayout.setHint(getString(transfer ? R.string.transfer_from : R.string.account_hint));
        payeeLayout.setHint(getString(transfer ? R.string.transfer_payee_hint : R.string.payee_hint));
        updateSaveEnabled();
    }

    // ---- Dynamische Split-Liste ----

    private void updateSaveEnabled() {
        boolean enabled;
        if (isTransferType()) {
            String from = textOf(editAccount).trim();
            String to = textOf(editAccountTo).trim();
            Long cents = parseAmountToCents(textOf(editAmount));
            enabled = !from.isEmpty() && !to.isEmpty() && !from.equalsIgnoreCase(to)
                    && cents != null && cents > 0;
        } else {
            enabled = splitCtl.isValid();
        }
        btnSaveNew.setEnabled(enabled);
        btnUpdate.setEnabled(enabled);
    }

    // ---- Datum ----

    private void updateDateField() {
        editDate.setText(dateDisplay.format(selectedDate.getTime()));
        btnToday.setVisibility(isToday(selectedDate) ? View.GONE : View.VISIBLE);
    }

    private boolean isToday(Calendar c) {
        Calendar now = Calendar.getInstance();
        return c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            dateChangedByUser = true;
            updateDateField();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Führt {@code proceed} aus; fragt das Datum nur nach, wenn eine bestehende Buchung als Vorlage geöffnet
     * wurde, deren (altes) Datum unverändert blieb und daraus eine neue Buchung angelegt wird (Kopieren).
     * Beim Ändern der bestehenden Buchung oder bei selbst gesetztem/heutigem Datum kommt keine Abfrage.
     */
    private void maybeDateConfirm(Runnable proceed) {
        if (!openedFromExistingBooking || dateChangedByUser || isToday(selectedDate)) {
            proceed.run();
            return;
        }
        String dateStr = dateDisplay.format(selectedDate.getTime());
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.date_confirm_title)
                .setMessage(getString(R.string.date_confirm_message, dateStr))
                .setPositiveButton(getString(R.string.date_use_given, dateStr), (d, w) -> proceed.run())
                .setNegativeButton(R.string.date_use_today, (d, w) -> {
                    selectedDate.setTime(new java.util.Date());
                    updateDateField();
                    proceed.run();
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    /** True, wenn das Konto mindestens einen Ort besitzt (steuert die Sichtbarkeit des Ortsfelds). */
    private boolean hasPlaces(String account) {
        return account != null && !account.trim().isEmpty()
                && !placesStore.getPlaces(account.trim()).isEmpty();
    }

    private void setupPlaceDropdown(String account) {
        List<String> options = new ArrayList<>(placesStore.getPlaces(account));
        options.add(PlacesStore.NO_PLACE);
        editPlace.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String def = placesStore.getDefaultPlace(account);
        String preset = (!def.isEmpty() && options.contains(def)) ? def : PlacesStore.NO_PLACE;
        editPlace.setText(preset, false);
    }

    /**
     * Befüllt ein Ort-Dropdown (Umbuchung) mit den Orten des Kontos; Standard ist „ohne Ort", sodass ohne
     * bewusste Auswahl keine Ortsbewegung entsteht. {@code keepCurrent} behält einen gültigen aktuellen Wert.
     */
    private void setupPlaceOptions(MaterialAutoCompleteTextView field, String account, boolean keepCurrent) {
        List<String> options = new ArrayList<>(placesStore.getPlaces(account));
        options.add(PlacesStore.NO_PLACE);
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String cur = textOf(field).trim();
        if (!(keepCurrent && !cur.isEmpty() && options.contains(cur))) {
            field.setText(PlacesStore.NO_PLACE, false);
        }
    }

    /** Ausgewählter Nach-Ort (Umbuchung), normalisiert: „ohne Ort"/leer → {@code ""}. */
    private String selectedPlaceTo() {
        String sel = textOf(editPlaceTo);
        return (sel != null && !sel.trim().isEmpty() && !sel.equals(PlacesStore.NO_PLACE))
                ? sel.trim() : "";
    }

    // ---- Speichern (neu) ----

    private void saveAsNew() {
        if (isTransferType()) {
            saveTransferNew();
            return;
        }
        Booking b = readValidFields(new Booking());
        if (b == null) {
            return;
        }
        b.exported = false;
        final List<SplitRowController.Part> parts = splitCtl.collectParts();
        b.category = parts.isEmpty() ? "" : parts.get(0).category;
        final String place = textOf(editPlace);
        maybeAskCorrection(b.payee, () -> maybeDateConfirm(() -> {
            b.createdAt = composeTimestamp();
            persistNew(b, place, parts);
        }));
    }

    private void saveTransferNew() {
        final String from = textOf(editAccount).trim();
        final String to = textOf(editAccountTo).trim();
        final Long cents = parseAmountToCents(textOf(editAmount));
        if (cents == null || cents <= 0) {
            Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to)) {
            Toast.makeText(this, R.string.error_transfer_accounts, Toast.LENGTH_SHORT).show();
            return;
        }
        final String note = textOf(editNote).trim();
        final String payee = textOf(editPayee).trim();
        final String fromPlace = selectedPlace();
        final String toPlace = selectedPlaceTo();
        maybeAskCorrection(payee, () -> maybeDateConfirm(() ->
                repository.saveTransferBooking(from, to, cents, payee, note,
                composeTimestamp(), fromPlace, toPlace, () -> {
                    Toast.makeText(this, R.string.transfer_saved, Toast.LENGTH_SHORT).show();
                    finish();
                })));
    }

    private void persistNew(Booking b, String place, List<SplitRowController.Part> parts) {
        // Ort wird an der Buchung gespeichert (Standardort ist ein echter Ort; „ohne Ort" → leer).
        final String fp = place;
        Runnable done = () -> {
            Toast.makeText(this, R.string.booking_saved, Toast.LENGTH_SHORT).show();
            finish();
        };
        if (parts.size() >= 2) {
            repository.saveSplitBooking(b, toSplits(parts), fp, done);
        } else {
            repository.saveBookingWithPlace(b, fp, done);
        }
    }

    /** Ausgewählter Ort normalisiert: „ohne Ort" bzw. leer → {@code ""}, sonst der echte Ortsname. */
    private String selectedPlace() {
        String sel = textOf(editPlace);
        return (sel != null && !sel.trim().isEmpty() && !sel.equals(PlacesStore.NO_PLACE))
                ? sel.trim() : "";
    }

    // ---- Aktualisieren (bestehende Buchung) ----

    private void update() {
        if (booking == null) {
            return;
        }
        boolean nowTransfer = isTransferType();
        if (origIsTransfer && nowTransfer) {
            updateTransferInPlace();
        } else if (!origIsTransfer && !nowTransfer) {
            updateNormalInPlace();
        } else if (!origIsTransfer) {
            convertNormalToTransfer();
        } else {
            convertTransferToNormal();
        }
    }

    private void updateNormalInPlace() {
        if (readValidFields(booking) == null) {
            return;
        }
        final List<SplitRowController.Part> parts = splitCtl.collectParts();
        booking.category = parts.isEmpty() ? "" : parts.get(0).category;
        booking.isTransfer = false;
        booking.transferAccount = "";
        booking.transferGroup = "";
        booking.exported = switchExported.isChecked();
        final boolean managed = origPlaceManaged;
        final String place = selectedPlace();
        maybeAskCorrection(booking.payee, () -> {
            booking.createdAt = composeTimestamp();
            final List<BookingSplit> splits = parts.size() >= 2 ? toSplits(parts) : new ArrayList<>();
            Runnable done = () -> {
                Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
                finish();
            };
            if (managed) {
                // App-Buchung: Ort-Journal per Ausgleichs-Bewegung nachziehen (Betrag/Ort/Löschung).
                repository.updateBookingWithPlace(booking, place, splits, done);
            } else {
                // Importierte Buchung: keine Ort-Verknüpfung → Ort-Journal unberührt lassen.
                repository.updateSplitBooking(booking, splits, done);
            }
        });
    }

    private void updateTransferInPlace() {
        final String from = textOf(editAccount).trim();
        final String to = textOf(editAccountTo).trim();
        final Long cents = parseAmountToCents(textOf(editAmount));
        if (cents == null || cents <= 0) {
            Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to)) {
            Toast.makeText(this, R.string.error_transfer_accounts, Toast.LENGTH_SHORT).show();
            return;
        }
        final String note = textOf(editNote).trim();
        final String payee = textOf(editPayee).trim();
        final String fromPlace = selectedPlace();
        final String toPlace = selectedPlaceTo();
        maybeAskCorrection(payee, () ->
                repository.updateTransferBooking(booking, from, to, cents, payee, note,
                composeTimestamp(), fromPlace, toPlace, () -> {
                    Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    private void convertNormalToTransfer() {
        final String from = textOf(editAccount).trim();
        final String to = textOf(editAccountTo).trim();
        final Long cents = parseAmountToCents(textOf(editAmount));
        if (cents == null || cents <= 0) {
            Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to)) {
            Toast.makeText(this, R.string.error_transfer_accounts, Toast.LENGTH_SHORT).show();
            return;
        }
        final String note = textOf(editNote).trim();
        final String payee = textOf(editPayee).trim();
        final String fromPlace = selectedPlace();
        final String toPlace = selectedPlaceTo();
        final long oldId = booking.id;
        maybeAskCorrection(payee, () -> {
            long ts = composeTimestamp();
            repository.deleteBooking(oldId, null);
            repository.saveTransferBooking(from, to, cents, payee, note, ts, fromPlace, toPlace, () -> {
                Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void convertTransferToNormal() {
        Booking nb = readValidFields(new Booking());
        if (nb == null) {
            return;
        }
        nb.exported = false;
        final List<SplitRowController.Part> parts = splitCtl.collectParts();
        nb.category = parts.isEmpty() ? "" : parts.get(0).category;
        final String place = textOf(editPlace);
        final String group = origTransferGroup;
        final long oldId = booking.id;
        maybeAskCorrection(nb.payee, () -> {
            nb.createdAt = composeTimestamp();
            repository.deleteTransfer(group, oldId, null);
            // Standardort ist jetzt ein echter Ort → keine Sonderbehandlung; „ohne Ort" filtert das Repository.
            final String fp = place;
            Runnable done = () -> {
                Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
                finish();
            };
            if (parts.size() >= 2) {
                repository.saveSplitBooking(nb, toSplits(parts), fp, done);
            } else {
                repository.saveBookingWithPlace(nb, fp, done);
            }
        });
    }

    private void confirmDelete() {
        if (booking == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Runnable done = () -> {
                        Toast.makeText(this, R.string.booking_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    };
                    if (origIsTransfer) {
                        repository.deleteTransfer(origTransferGroup, booking.id, done);
                    } else {
                        repository.deleteBooking(booking.id, done);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Validiert die gemeinsamen Felder (ohne Kategorie) und schreibt sie in {@code target}. */
    private Booking readValidFields(Booking target) {
        Long cents = parseAmountToCents(textOf(editAmount));
        if (cents == null || cents <= 0) {
            Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
            return null;
        }
        String payee = textOf(editPayee).trim();
        if (payee.isEmpty()) {
            Toast.makeText(this, R.string.error_payee, Toast.LENGTH_SHORT).show();
            return null;
        }
        String account = textOf(editAccount).trim();
        if (account.isEmpty()) {
            Toast.makeText(this, R.string.error_account, Toast.LENGTH_SHORT).show();
            return null;
        }
        target.amountCents = cents;
        target.isIncome = toggleType.getCheckedButtonId() == R.id.btnIncome;
        target.payee = payee;
        target.account = account;
        target.note = textOf(editNote).trim();
        target.createdAt = composeTimestamp();
        return target;
    }

    private List<BookingSplit> toSplits(List<SplitRowController.Part> parts) {
        List<BookingSplit> out = new ArrayList<>();
        for (SplitRowController.Part p : parts) {
            out.add(new BookingSplit(0, p.category, p.cents));
        }
        return out;
    }

    private long composeTimestamp() {
        Calendar time = Calendar.getInstance();
        if (booking != null) {
            time.setTimeInMillis(booking.createdAt);
        }
        Calendar c = (Calendar) selectedDate.clone();
        c.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
        c.set(Calendar.SECOND, time.get(Calendar.SECOND));
        c.set(Calendar.MILLISECOND, time.get(Calendar.MILLISECOND));
        return c.getTimeInMillis();
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
}
