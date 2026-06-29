package de.spahr.ausgaben.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.text.TextUtils;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.ExportCoordinator;
import de.spahr.ausgaben.settings.SettingsStore;

public class MainActivity extends AppCompatActivity {

    private Repository repository;
    private SettingsStore settings;

    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText editAmount;
    private MaterialAutoCompleteTextView editPayee;
    private MaterialAutoCompleteTextView editAccount;
    private TextInputEditText editNote;
    private TextInputEditText editDate;
    private BookingAdapter adapter;

    private final SimpleDateFormat dateDisplay = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final Calendar selectedDate = Calendar.getInstance();

    private List<Booking> allBookings = new ArrayList<>();
    private String filterPayee = "";
    private String filterAccount = "";
    private Long filterAmountCents = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        repository = new Repository(this);
        settings = new SettingsStore(this);

        toggleType = findViewById(R.id.toggleType);
        editAmount = findViewById(R.id.editAmount);
        // Locale-unabhängig: Ziffern sowie Komma und Punkt als Dezimaltrennzeichen erlauben.
        editAmount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        editPayee = findViewById(R.id.editPayee);
        editAccount = findViewById(R.id.editAccount);
        editNote = findViewById(R.id.editNote);
        editDate = findViewById(R.id.editDate);

        updateDateField();
        editDate.setOnClickListener(v -> showDatePicker());

        // Standardauswahl: Ausgabe
        toggleType.check(R.id.btnExpense);

        RecyclerView recycler = findViewById(R.id.recyclerBookings);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingAdapter();
        adapter.setListener(new BookingAdapter.Listener() {
            @Override
            public void onClick(Booking b) {
                duplicateIntoForm(b);
            }

            @Override
            public void onLongClick(Booking b) {
                Intent i = new Intent(MainActivity.this, BookingEditActivity.class);
                i.putExtra(BookingEditActivity.EXTRA_BOOKING_ID, b.id);
                startActivity(i);
            }
        });
        recycler.setAdapter(adapter);

        MaterialButton btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveBooking());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBookings();
        refreshSuggestions();
        prefillDefaultAccount();
    }

    private void updateDateField() {
        editDate.setText(dateDisplay.format(selectedDate.getTime()));
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            updateDateField();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void prefillDefaultAccount() {
        String def = settings.getDefaultAccount();
        if (!def.isEmpty() && TextUtils.isEmpty(editAccount.getText())) {
            editAccount.setText(def, false);
            repository.ensureAccount(def);
        }
    }

    private void refreshSuggestions() {
        repository.getPayeeNames(names -> editPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> editAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
    }

    private void refreshBookings() {
        repository.getAllBookings(result -> {
            allBookings = result;
            applyFilter();
        });
    }

    /** Kurzer Druck auf eine Buchung: Formular mit ihren Werten vorbelegen (Duplizieren). */
    private void duplicateIntoForm(Booking b) {
        editAmount.setText(formatCents(b.amountCents));
        editPayee.setText(b.payee);
        editAccount.setText(b.account, false);
        editNote.setText(b.note);
        toggleType.check(b.isIncome ? R.id.btnIncome : R.id.btnExpense);
        Toast.makeText(this, R.string.duplicated, Toast.LENGTH_SHORT).show();
        editAmount.requestFocus();
    }

    private String formatCents(long cents) {
        long euros = cents / 100;
        long rest = Math.abs(cents % 100);
        return euros + "," + String.format(Locale.GERMANY, "%02d", rest);
    }

    private void saveBooking() {
        Long cents = parseAmountToCents(textOf(editAmount));
        if (cents == null || cents <= 0) {
            Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        String payee = textOf(editPayee).trim();
        if (payee.isEmpty()) {
            Toast.makeText(this, R.string.error_payee, Toast.LENGTH_SHORT).show();
            return;
        }
        String account = textOf(editAccount).trim();
        if (account.isEmpty()) {
            Toast.makeText(this, R.string.error_account, Toast.LENGTH_SHORT).show();
            return;
        }

        Booking b = new Booking();
        b.amountCents = cents;
        b.isIncome = toggleType.getCheckedButtonId() == R.id.btnIncome;
        b.payee = payee;
        b.account = account;
        b.note = textOf(editNote).trim();
        b.createdAt = composeTimestamp();
        b.exported = false;

        repository.saveBooking(b, () -> {
            Toast.makeText(this, R.string.booking_saved, Toast.LENGTH_SHORT).show();
            editAmount.setText("");
            editPayee.setText("");
            editNote.setText("");
            // Konto-Vorbelegung beibehalten; nur leeren, wenn kein Standardkonto gesetzt ist.
            String def = settings.getDefaultAccount();
            editAccount.setText(def.isEmpty() ? "" : def, false);
            toggleType.check(R.id.btnExpense);
            // Datum auf heute zurücksetzen
            selectedDate.setTime(new java.util.Date());
            updateDateField();
            refreshBookings();
            refreshSuggestions();
        });
    }

    /** Gewähltes Datum mit der aktuellen Uhrzeit kombinieren (stabile Sortierung). */
    private long composeTimestamp() {
        Calendar now = Calendar.getInstance();
        Calendar c = (Calendar) selectedDate.clone();
        c.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
        c.set(Calendar.SECOND, now.get(Calendar.SECOND));
        c.set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND));
        return c.getTimeInMillis();
    }

    /** Parst "12,50" oder "12.50" zu Cent. Gibt null bei ungültiger Eingabe zurück. */
    private Long parseAmountToCents(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(" ", "").replace(",", ".");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(normalized);
            return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private String textOf(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    // ---- Filter ----

    private void applyFilter() {
        List<Booking> filtered = new ArrayList<>();
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
        }
        adapter.setItems(filtered);
        boolean active = !filterPayee.isEmpty() || !filterAccount.isEmpty() || filterAmountCents != null;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(active
                    ? getString(R.string.filter_active, filtered.size()) : null);
        }
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

        new AlertDialog.Builder(this)
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
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
            startActivity(new Intent(this, AnalysisActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doExport() {
        Toast.makeText(this, R.string.export_running, Toast.LENGTH_SHORT).show();
        new ExportCoordinator(this, repository, settings).exportUnexported((message, refreshNeeded) -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            if (refreshNeeded) {
                refreshBookings();
            }
        });
    }
}
