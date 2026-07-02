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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Vereinheitlichter Editor für Neueingabe und Bearbeitung.
 * - Neu-Modus (kein {@link #EXTRA_BOOKING_ID}): leeres Formular, ein Button „Neue Buchung".
 * - Bearbeiten-Modus: Felder geladen, drei Buttons „Neue Buchung" / „Buchung ändern" / „Buchung löschen".
 */
public class BookingEditActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";

    private Repository repository;
    private SettingsStore settings;
    private PlacesStore placesStore;
    private Booking booking; // null = Neu-Modus

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText editAmount;
    private MaterialAutoCompleteTextView editPayee;
    private MaterialAutoCompleteTextView editAccount;
    private MaterialAutoCompleteTextView editCategory;
    private MaterialAutoCompleteTextView editPlace;
    private TextInputEditText editNote;
    private TextInputEditText editDate;
    private com.google.android.material.materialswitch.MaterialSwitch switchExported;
    private MaterialButton btnToday;
    private MaterialButton btnUpdate;
    private MaterialButton btnDelete;

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
        editPayee = findViewById(R.id.editPayee);
        editAccount = findViewById(R.id.editAccount);
        editCategory = findViewById(R.id.editCategory);
        editPlace = findViewById(R.id.editPlace);
        editNote = findViewById(R.id.editNote);
        editDate = findViewById(R.id.editDate);
        switchExported = findViewById(R.id.switchExported);
        editDate.setOnClickListener(v -> showDatePicker());

        btnToday = findViewById(R.id.btnToday);
        btnToday.setOnClickListener(v -> {
            selectedDate.setTime(new java.util.Date());
            updateDateField();
        });

        btnUpdate = findViewById(R.id.btnUpdate);
        btnDelete = findViewById(R.id.btnDelete);

        repository.getPayeeNames(names -> editPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> editAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getCategoryNames(names -> editCategory.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        // Ort-Dropdown folgt dem gewählten Konto.
        editAccount.setOnItemClickListener((parent, view, position, id) ->
                setupPlaceDropdown(textOf(editAccount).trim()));

        findViewById(R.id.btnSaveNew).setOnClickListener(v -> saveAsNew());
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
        toolbar.setTitle(R.string.new_booking_title);
        toggleType.check(R.id.btnExpense);
        selectedDate.setTime(new java.util.Date());
        updateDateField();
        String def = settings.getDefaultAccount();
        if (!def.isEmpty()) {
            editAccount.setText(def, false);
        }
        setupPlaceDropdown(def);
        switchExported.setVisibility(View.GONE);
        btnUpdate.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
    }

    private void bindEditMode(Booking b) {
        if (b == null) {
            finish();
            return;
        }
        booking = b;
        toolbar.setTitle(R.string.edit_title);
        toggleType.check(b.isIncome ? R.id.btnIncome : R.id.btnExpense);
        editAmount.setText(formatCents(b.amountCents));
        editPayee.setText(b.payee);
        editAccount.setText(b.account, false);
        setupPlaceDropdown(b.account);
        editCategory.setText(b.category, false);
        editNote.setText(b.note);
        selectedDate.setTimeInMillis(b.createdAt);
        updateDateField();
        switchExported.setVisibility(View.VISIBLE);
        switchExported.setChecked(b.exported);
        btnUpdate.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
    }

    private void updateDateField() {
        editDate.setText(dateDisplay.format(selectedDate.getTime()));
        // „Heute"-Button nur zeigen, wenn das gewählte Datum nicht der heutige Tag ist.
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

    /** Ort-Dropdown auf die Orte des gewählten Kontos setzen; Vorauswahl = Standardort des Kontos. */
    private void setupPlaceDropdown(String account) {
        List<String> options = new ArrayList<>(placesStore.getPlaces(account));
        options.add(PlacesStore.NO_PLACE);
        editPlace.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String def = placesStore.getDefaultPlace(account);
        String preset = (!def.isEmpty() && options.contains(def)) ? def : PlacesStore.NO_PLACE;
        editPlace.setText(preset, false);
    }

    /** Legt aus den aktuellen Feldwerten eine NEUE Buchung an (auch als „Duplizieren" im Bearbeiten-Modus). */
    private void saveAsNew() {
        Booking b = readValidFields(new Booking());
        if (b == null) {
            return;
        }
        b.exported = false;
        String place = editPlace.getText() == null ? "" : editPlace.getText().toString();

        if (!isToday(selectedDate)) {
            String dateStr = dateDisplay.format(selectedDate.getTime());
            new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                    .setTitle(R.string.date_confirm_title)
                    .setMessage(getString(R.string.date_confirm_message, dateStr))
                    .setPositiveButton(getString(R.string.date_use_given, dateStr),
                            (d, w) -> persistNew(b, place))
                    .setNegativeButton(R.string.date_use_today, (d, w) -> {
                        b.createdAt = System.currentTimeMillis();
                        persistNew(b, place);
                    })
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        } else {
            persistNew(b, place);
        }
    }

    private void persistNew(Booking b, String place) {
        // Standardort des Kontos = Rest-Topf → keine explizite Ort-Bewegung (fließt automatisch dorthin).
        String p = place;
        if (p != null && p.equals(placesStore.getDefaultPlace(b.account))) {
            p = "";
        }
        repository.saveBookingWithPlace(b, p, () -> {
            Toast.makeText(this, R.string.booking_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** Aktualisiert die bestehende Buchung. */
    private void update() {
        if (booking == null) {
            return;
        }
        if (readValidFields(booking) == null) {
            return;
        }
        booking.exported = switchExported.isChecked();
        repository.updateBooking(booking, () -> {
            Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void confirmDelete() {
        if (booking == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteBooking(booking.id, () -> {
                    Toast.makeText(this, R.string.booking_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Validiert die Felder und schreibt sie in {@code target}; gibt null bei Fehler zurück. */
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
        target.category = textOf(editCategory).trim();
        target.note = textOf(editNote).trim();
        target.createdAt = composeTimestamp();
        return target;
    }

    /** Gewähltes Datum mit Uhrzeit kombinieren: Originalzeit beibehalten, sonst aktuelle Uhrzeit. */
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
}
