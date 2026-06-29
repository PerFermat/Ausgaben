package de.spahr.ausgaben.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;

public class BookingEditActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";

    private Repository repository;
    private Booking booking;

    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText editAmount;
    private MaterialAutoCompleteTextView editPayee;
    private MaterialAutoCompleteTextView editAccount;
    private TextInputEditText editNote;
    private TextInputEditText editDate;

    private final SimpleDateFormat dateDisplay = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final Calendar selectedDate = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_booking);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);

        toggleType = findViewById(R.id.toggleType);
        editAmount = findViewById(R.id.editAmount);
        editAmount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        editPayee = findViewById(R.id.editPayee);
        editAccount = findViewById(R.id.editAccount);
        editNote = findViewById(R.id.editNote);
        editDate = findViewById(R.id.editDate);
        editDate.setOnClickListener(v -> showDatePicker());

        repository.getPayeeNames(names -> editPayee.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));
        repository.getAccountNames(names -> editAccount.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names)));

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());

        long id = getIntent().getLongExtra(EXTRA_BOOKING_ID, -1);
        if (id < 0) {
            finish();
            return;
        }
        repository.getBookingById(id, this::bind);
    }

    private void bind(Booking b) {
        if (b == null) {
            finish();
            return;
        }
        booking = b;
        toggleType.check(b.isIncome ? R.id.btnIncome : R.id.btnExpense);
        editAmount.setText(formatCents(b.amountCents));
        editPayee.setText(b.payee);
        editAccount.setText(b.account, false);
        editNote.setText(b.note);
        selectedDate.setTimeInMillis(b.createdAt);
        updateDateField();
    }

    private void updateDateField() {
        editDate.setText(dateDisplay.format(selectedDate.getTime()));
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

    private void save() {
        if (booking == null) {
            return;
        }
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

        booking.amountCents = cents;
        booking.isIncome = toggleType.getCheckedButtonId() == R.id.btnIncome;
        booking.payee = payee;
        booking.account = account;
        booking.note = textOf(editNote).trim();
        booking.createdAt = composeTimestamp();

        repository.updateBooking(booking, () -> {
            Toast.makeText(this, R.string.booking_updated, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void confirmDelete() {
        if (booking == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteBooking(booking.id, () -> {
                    Toast.makeText(this, R.string.booking_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Behält die ursprüngliche Uhrzeit der Buchung bei, ändert nur das Datum. */
    private long composeTimestamp() {
        Calendar original = Calendar.getInstance();
        original.setTimeInMillis(booking.createdAt);
        Calendar c = (Calendar) selectedDate.clone();
        c.set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY));
        c.set(Calendar.MINUTE, original.get(Calendar.MINUTE));
        c.set(Calendar.SECOND, original.get(Calendar.SECOND));
        c.set(Calendar.MILLISECOND, original.get(Calendar.MILLISECOND));
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
