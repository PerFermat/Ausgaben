package de.spahr.ausgaben.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Vereinheitlichter Editor für Neueingabe und Bearbeitung.
 * Unterstützt drei Typen: Ausgabe / Umbuchung / Einnahme. Bei Ausgabe/Einnahme können mehrere
 * Kategorien mit Teilbeträgen erfasst werden (Splitbuchung); bei Umbuchung zwei Konten (Von/Nach).
 */
public class BookingEditActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;
    private Booking booking; // null = Neu-Modus

    // Ursprünglicher Typ beim Bearbeiten (für Umbuchung ↔ normale Buchung Umwandlungen).
    private boolean origIsTransfer;
    private String origTransferGroup = "";

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText editAmount;
    private TextInputLayout payeeLayout;
    private MaterialAutoCompleteTextView editPayee;
    private TextInputLayout accountLayout;
    private MaterialAutoCompleteTextView editAccount;
    private TextInputLayout accountToLayout;
    private MaterialAutoCompleteTextView editAccountTo;
    private TextInputLayout placeLayout;
    private MaterialAutoCompleteTextView editPlace;
    private View splitSection;
    private android.widget.LinearLayout splitContainer;
    private TextInputEditText editNote;
    private TextInputEditText editDate;
    private com.google.android.material.materialswitch.MaterialSwitch switchExported;
    private MaterialButton btnToday;
    private MaterialButton btnSaveNew;
    private MaterialButton btnUpdate;
    private MaterialButton btnDelete;

    private ArrayAdapter<String> categoryAdapter;
    /** Unterdrückt die dynamische Split-Logik während des programmatischen Befüllens. */
    private boolean suppressSplitEvents;
    /** Verhindert Rückkopplung beim gegenseitigen Abgleich von Gesamtbetrag und Teilbeträgen. */
    private boolean syncingAmounts;

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
        splitSection = findViewById(R.id.splitSection);
        splitContainer = findViewById(R.id.splitContainer);
        editNote = findViewById(R.id.editNote);
        editDate = findViewById(R.id.editDate);
        switchExported = findViewById(R.id.switchExported);
        editDate.setOnClickListener(v -> showDatePicker());

        btnToday = findViewById(R.id.btnToday);
        btnToday.setOnClickListener(v -> {
            selectedDate.setTime(new java.util.Date());
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
        repository.getCategoryNames(names -> {
            categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
            applyCategoryAdapterToRows();
        });

        // Ort-Dropdown folgt dem gewählten Konto (nur bei Ausgabe/Einnahme relevant).
        editAccount.setOnItemClickListener((parent, view, position, id) ->
                setupPlaceDropdown(textOf(editAccount).trim()));

        // Gesamtbetrag ↔ Teilbeträge koppeln; Konto wirkt auf die Freischaltung der Buttons.
        editAmount.addTextChangedListener(new SimpleWatcher(this::onTotalChanged));
        editAccount.addTextChangedListener(new SimpleWatcher(this::updateSaveEnabled));
        editAccountTo.addTextChangedListener(new SimpleWatcher(this::updateSaveEnabled));

        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                applyTypeVisibility();
            }
        });

        btnSaveNew.setOnClickListener(v -> saveAsNew());
        btnUpdate.setOnClickListener(v -> update());
        btnDelete.setOnClickListener(v -> confirmDelete());

        long id = getIntent().getLongExtra(EXTRA_BOOKING_ID, -1);
        if (id < 0) {
            setupNewMode();
        } else {
            repository.getBookingById(id, this::bindEditMode);
        }
    }

    private void setupNewMode() {
        booking = null;
        origIsTransfer = false;
        origTransferGroup = "";
        toolbar.setTitle(R.string.new_booking_title);
        toggleType.check(R.id.btnExpense);
        selectedDate.setTime(new java.util.Date());
        updateDateField();
        String def = settings.getDefaultAccount();
        if (!def.isEmpty()) {
            editAccount.setText(def, false);
        }
        setupPlaceDropdown(def);
        splitContainer.removeAllViews();
        ensureTrailingRow();
        switchExported.setVisibility(View.GONE);
        btnUpdate.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        applyTypeVisibility();
        updateSaveEnabled();
    }

    private void bindEditMode(Booking b) {
        if (b == null) {
            finish();
            return;
        }
        booking = b;
        origIsTransfer = b.isTransfer;
        origTransferGroup = b.transferGroup == null ? "" : b.transferGroup;
        toolbar.setTitle(R.string.edit_title);
        editAmount.setText(formatCents(b.amountCents));
        editNote.setText(b.note);
        selectedDate.setTimeInMillis(b.createdAt);
        updateDateField();
        switchExported.setVisibility(b.isTransfer ? View.GONE : View.VISIBLE);
        switchExported.setChecked(b.exported);
        btnUpdate.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);

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
            applyTypeVisibility();
            updateSaveEnabled();
            return;
        }

        toggleType.check(b.isIncome ? R.id.btnIncome : R.id.btnExpense);
        editPayee.setText(b.payee);
        editAccount.setText(b.account, false);
        setupPlaceDropdown(b.account);
        applyTypeVisibility();
        // Kategorie-Teile laden (oder Einzelkategorie als eine Zeile).
        repository.getSplits(b.id, splits -> {
            suppressSplitEvents = true;
            splitContainer.removeAllViews();
            if (splits != null && !splits.isEmpty()) {
                for (BookingSplit s : splits) {
                    addSplitRow(s.category, formatCents(s.amountCents));
                }
            } else if (!b.category.isEmpty()) {
                addSplitRow(b.category, formatCents(b.amountCents));
            }
            suppressSplitEvents = false;
            ensureTrailingRow();
            updateSaveEnabled();
        });
    }

    private boolean isTransferType() {
        return toggleType.getCheckedButtonId() == R.id.btnTransfer;
    }

    /** Blendet Felder je nach Typ ein/aus (Umbuchung: zwei Konten, keine Kategorie/Ort/Empfänger). */
    private void applyTypeVisibility() {
        boolean transfer = isTransferType();
        accountToLayout.setVisibility(transfer ? View.VISIBLE : View.GONE);
        // Empfänger gibt es auch bei einer Umbuchung („Zahlungsempfänger"); Ort/Kategorien nicht.
        payeeLayout.setVisibility(View.VISIBLE);
        placeLayout.setVisibility(transfer ? View.GONE : View.VISIBLE);
        splitSection.setVisibility(transfer ? View.GONE : View.VISIBLE);
        accountLayout.setHint(getString(transfer ? R.string.transfer_from : R.string.account_hint));
        payeeLayout.setHint(getString(transfer ? R.string.transfer_payee_hint : R.string.payee_hint));
        updateSaveEnabled();
    }

    // ---- Dynamische Split-Liste ----

    private void addSplitRow(String category, String amountText) {
        View row = getLayoutInflater().inflate(R.layout.item_split_row, splitContainer, false);
        MaterialAutoCompleteTextView cat = row.findViewById(R.id.splitCategory);
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        View remove = row.findViewById(R.id.btnRemoveSplit);
        if (categoryAdapter != null) {
            cat.setAdapter(categoryAdapter);
        }
        // Vorbelegung vor dem Anhängen der Listener, damit sie keine dynamische Logik auslösen.
        if (category != null) {
            cat.setText(category, false);
        }
        if (amountText != null) {
            amt.setText(amountText);
        }
        cat.addTextChangedListener(new SimpleWatcher(() -> onSplitCategoryChanged(row)));
        amt.addTextChangedListener(new SimpleWatcher(() -> onPartialChanged(row)));
        remove.setOnClickListener(v -> {
            splitContainer.removeView(row);
            ensureTrailingRow();
            recomputeTotalFromParts();
            updateSaveEnabled();
        });
        splitContainer.addView(row);
    }

    private void onSplitCategoryChanged(View row) {
        if (suppressSplitEvents) {
            return;
        }
        int idx = splitContainer.indexOfChild(row);
        String cat = catText(row);
        // Erste Kategorie → Teilbetrag automatisch mit dem (vorhandenen) Gesamtbetrag vorbelegen.
        if (idx == 0 && !cat.isEmpty() && amtText(row).isEmpty() && currentTotalCents() > 0) {
            setAmtText(row, formatCents(currentTotalCents()));
        }
        // Kategorie in der letzten Zeile → neue leere Zeile anhängen.
        if (!cat.isEmpty() && idx == splitContainer.getChildCount() - 1) {
            addSplitRow(null, null);
        }
        recomputeTotalFromParts();
        updateSaveEnabled();
    }

    /** Teilbetrag geändert → Gesamtbetrag = Summe der Teilbeträge. */
    private void onPartialChanged(View row) {
        if (suppressSplitEvents || syncingAmounts) {
            updateSaveEnabled();
            return;
        }
        recomputeTotalFromParts();
        updateSaveEnabled();
    }

    /** Gesamtbetrag geändert → bei genau einer Kategorie deren Teilbetrag gleich dem Gesamtbetrag setzen. */
    private void onTotalChanged() {
        if (suppressSplitEvents || syncingAmounts) {
            updateSaveEnabled();
            return;
        }
        View single = singleCategoryRow();
        if (single != null) {
            syncingAmounts = true;
            setAmtText(single, formatCents(currentTotalCents()));
            syncingAmounts = false;
        }
        updateSaveEnabled();
    }

    /** Setzt den Gesamtbetrag auf die Summe aller Teilbeträge (Zeilen mit Kategorie). */
    private void recomputeTotalFromParts() {
        long sum = 0;
        boolean any = false;
        for (int i = 0; i < splitContainer.getChildCount(); i++) {
            View r = splitContainer.getChildAt(i);
            if (catText(r).isEmpty()) {
                continue;
            }
            Long c = parseAmountToCents(amtText(r));
            if (c != null) {
                sum += c;
                any = true;
            }
        }
        if (!any) {
            return; // keine Kategorie mit Betrag → Gesamtbetrag unverändert lassen
        }
        syncingAmounts = true;
        editAmount.setText(formatCents(sum));
        syncingAmounts = false;
    }

    /** Liefert die einzige Zeile mit gesetzter Kategorie oder {@code null}, wenn es 0 oder mehrere sind. */
    private View singleCategoryRow() {
        View found = null;
        for (int i = 0; i < splitContainer.getChildCount(); i++) {
            View r = splitContainer.getChildAt(i);
            if (!catText(r).isEmpty()) {
                if (found != null) {
                    return null;
                }
                found = r;
            }
        }
        return found;
    }

    /** Sorgt für genau eine leere Abschluss-Zeile am Ende. */
    private void ensureTrailingRow() {
        int n = splitContainer.getChildCount();
        if (n == 0) {
            addSplitRow(null, null);
            return;
        }
        if (!catText(splitContainer.getChildAt(n - 1)).isEmpty()) {
            addSplitRow(null, null);
        }
    }

    private void applyCategoryAdapterToRows() {
        for (int i = 0; i < splitContainer.getChildCount(); i++) {
            MaterialAutoCompleteTextView cat = splitContainer.getChildAt(i).findViewById(R.id.splitCategory);
            if (cat != null) {
                cat.setAdapter(categoryAdapter);
            }
        }
    }

    /** Kategorie-Teile mit gültiger Kategorie und Betrag (leere Abschlusszeile wird ignoriert). */
    private List<Part> collectParts() {
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < splitContainer.getChildCount(); i++) {
            View r = splitContainer.getChildAt(i);
            String c = catText(r);
            if (c.isEmpty()) {
                continue;
            }
            Long cents = parseAmountToCents(amtText(r));
            if (cents != null) {
                parts.add(new Part(c, cents));
            }
        }
        return parts;
    }

    /** true, wenn das Ausgabe/Einnahme-Formular gespeichert werden darf (Summe der Teile = Gesamt). */
    private boolean computeSplitValid() {
        Long total = parseAmountToCents(textOf(editAmount));
        if (total == null || total <= 0) {
            return false;
        }
        long sum = 0;
        int count = 0;
        for (int i = 0; i < splitContainer.getChildCount(); i++) {
            View r = splitContainer.getChildAt(i);
            String c = catText(r);
            String a = amtText(r);
            if (c.isEmpty()) {
                continue; // leere / betragslose Kategoriezeile ignorieren
            }
            Long cents = parseAmountToCents(a);
            if (cents == null) {
                return false; // Kategorie ohne gültigen Teilbetrag
            }
            sum += cents;
            count++;
        }
        if (count == 0) {
            return true; // keine Kategorie → einfache (nicht zugeordnete) Buchung
        }
        return sum == total;
    }

    private void updateSaveEnabled() {
        boolean enabled;
        if (isTransferType()) {
            String from = textOf(editAccount).trim();
            String to = textOf(editAccountTo).trim();
            Long cents = parseAmountToCents(textOf(editAmount));
            enabled = !from.isEmpty() && !to.isEmpty() && !from.equalsIgnoreCase(to)
                    && cents != null && cents > 0;
        } else {
            enabled = computeSplitValid();
        }
        btnSaveNew.setEnabled(enabled);
        btnUpdate.setEnabled(enabled);
    }

    private String catText(View row) {
        MaterialAutoCompleteTextView cat = row.findViewById(R.id.splitCategory);
        return cat.getText() == null ? "" : cat.getText().toString().trim();
    }

    private String amtText(View row) {
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        return amt.getText() == null ? "" : amt.getText().toString().trim();
    }

    private void setAmtText(View row, String text) {
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        amt.setText(text);
    }

    private long currentTotalCents() {
        Long t = parseAmountToCents(textOf(editAmount));
        return t == null ? 0 : t;
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
            updateDateField();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Führt {@code proceed} aus; bei abweichendem Datum vorher nachfragen (heute/gegeben). */
    private void withDateConfirm(Runnable proceed) {
        if (isToday(selectedDate)) {
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

    private void setupPlaceDropdown(String account) {
        List<String> options = new ArrayList<>(placesStore.getPlaces(account));
        options.add(PlacesStore.NO_PLACE);
        editPlace.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String def = placesStore.getDefaultPlace(account);
        String preset = (!def.isEmpty() && options.contains(def)) ? def : PlacesStore.NO_PLACE;
        editPlace.setText(preset, false);
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
        final List<Part> parts = collectParts();
        b.category = parts.isEmpty() ? "" : parts.get(0).category;
        final String place = textOf(editPlace);
        withDateConfirm(() -> {
            b.createdAt = composeTimestamp();
            persistNew(b, place, parts);
        });
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
        withDateConfirm(() -> repository.saveTransferBooking(from, to, cents, payee, note,
                composeTimestamp(), () -> {
                    Toast.makeText(this, R.string.transfer_saved, Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    private void persistNew(Booking b, String place, List<Part> parts) {
        String p = place;
        if (p != null && p.equals(placesStore.getDefaultPlace(b.account))) {
            p = "";
        }
        final String fp = p;
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
        final List<Part> parts = collectParts();
        booking.category = parts.isEmpty() ? "" : parts.get(0).category;
        booking.isTransfer = false;
        booking.transferAccount = "";
        booking.transferGroup = "";
        booking.exported = switchExported.isChecked();
        withDateConfirm(() -> {
            booking.createdAt = composeTimestamp();
            repository.updateSplitBooking(booking,
                    parts.size() >= 2 ? toSplits(parts) : new ArrayList<>(), () -> {
                        Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
                        finish();
                    });
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
        withDateConfirm(() -> repository.updateTransferBooking(booking, from, to, cents, payee, note,
                composeTimestamp(), () -> {
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
        final long oldId = booking.id;
        withDateConfirm(() -> {
            long ts = composeTimestamp();
            repository.deleteBooking(oldId, null);
            repository.saveTransferBooking(from, to, cents, payee, note, ts, () -> {
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
        final List<Part> parts = collectParts();
        nb.category = parts.isEmpty() ? "" : parts.get(0).category;
        final String place = textOf(editPlace);
        final String group = origTransferGroup;
        final long oldId = booking.id;
        withDateConfirm(() -> {
            nb.createdAt = composeTimestamp();
            repository.deleteTransfer(group, oldId, null);
            String p = place;
            if (p != null && p.equals(placesStore.getDefaultPlace(nb.account))) {
                p = "";
            }
            final String fp = p;
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

    private List<BookingSplit> toSplits(List<Part> parts) {
        List<BookingSplit> out = new ArrayList<>();
        for (Part p : parts) {
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
        long euros = cents / 100;
        long rest = Math.abs(cents % 100);
        String sign = (cents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", rest);
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

    /** Einfacher Kategorie/Teilbetrag-Datensatz während der Editor-Eingabe. */
    private static final class Part {
        final String category;
        final long cents;

        Part(String category, long cents) {
            this.category = category;
            this.cents = cents;
        }
    }

    /** TextWatcher, der bei jeder Änderung eine Aktion ausführt. */
    private static final class SimpleWatcher implements android.text.TextWatcher {
        private final Runnable action;

        SimpleWatcher(Runnable action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            action.run();
        }
    }
}
