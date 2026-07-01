package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.PlaceBalance;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/** Bestands-Übersicht: Saldo je Ort, „ohne Ort", Gesamt; Umbuchen und Kassensturz; Ort → Verlauf. */
public class BalanceActivity extends AppCompatActivity {

    private Repository repository;
    private PlacesStore placesStore;
    private SettingsStore settings;
    private LinearLayout container;

    private Map<String, Long> balances = new LinkedHashMap<>();
    private long total = 0;
    private long allPlaceSum = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        placesStore = new PlacesStore(this);
        settings = new SettingsStore(this);
        container = findViewById(R.id.balanceContainer);

        ((MaterialButton) findViewById(R.id.btnTransfer)).setOnClickListener(v -> showTransferDialog());
        ((MaterialButton) findViewById(R.id.btnReconcile)).setOnClickListener(v -> showReconcileDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        repository.getTotalBalance(t -> {
            total = t;
            repository.getPlaceBalances(pb -> {
                balances = new LinkedHashMap<>();
                allPlaceSum = 0;
                for (PlaceBalance b : pb) {
                    balances.put(b.place, b.balanceCents);
                    allPlaceSum += b.balanceCents;
                }
                render();
            });
        });
    }

    private void render() {
        container.removeAllViews();
        for (String place : placesStore.getPlaces()) {
            long bal = balances.containsKey(place) ? balances.get(place) : 0L;
            addRow(place, bal, true, v -> {
                Intent i = new Intent(this, PlaceHistoryActivity.class);
                i.putExtra(PlaceHistoryActivity.EXTRA_PLACE, place);
                startActivity(i);
            });
        }
        addRow(getString(R.string.no_place), total - allPlaceSum, false, null);

        View divider = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dp.topMargin = 16;
        dp.bottomMargin = 8;
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(getColor(R.color.grey_text));
        container.addView(divider);

        addRow(getString(R.string.saldo_total), total, false, null);
    }

    private void addRow(String label, long cents, boolean clickable, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 28, 0, 28);
        if (clickable) {
            row.setClickable(true);
            row.setOnClickListener(onClick);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
        }

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(17f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText(formatEuro(cents));
        value.setTextSize(17f);
        value.setGravity(Gravity.END);
        value.setTextColor(cents < 0 ? getColor(R.color.expense_red) : getColor(R.color.income_green));

        row.addView(name);
        row.addView(value);
        container.addView(row);
    }

    // ---- Umbuchen ----

    private void showTransferDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_transfer, null, false);
        MaterialAutoCompleteTextView from = view.findViewById(R.id.transferFrom);
        MaterialAutoCompleteTextView to = view.findViewById(R.id.transferTo);
        TextInputEditText amount = view.findViewById(R.id.transferAmount);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        List<String> options = placeOptions();
        from.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        to.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        if (!options.isEmpty()) {
            from.setText(options.get(0), false);
            to.setText(options.get(options.size() - 1), false);
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.transfer_title)
                .setView(view)
                .setPositiveButton(R.string.transfer_do, (d, w) -> {
                    String f = textOf(from);
                    String t = textOf(to);
                    Long cents = parseCents(textOf(amount));
                    if (cents == null || cents <= 0 || f.equals(t)) {
                        Toast.makeText(this, R.string.transfer_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.saveTransfer(f, t, cents, this::refresh);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Kassensturz ----

    private void showReconcileDialog() {
        List<String> places = placesStore.getPlaces();
        if (places.isEmpty()) {
            Toast.makeText(this, R.string.no_places_defined, Toast.LENGTH_LONG).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reconcile, null, false);
        MaterialAutoCompleteTextView place = view.findViewById(R.id.reconcilePlace);
        TextInputEditText amount = view.findViewById(R.id.reconcileAmount);
        com.google.android.material.checkbox.MaterialCheckBox createBooking =
                view.findViewById(R.id.reconcileCreateBooking);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        place.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, places));
        place.setText(places.get(0), false);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.reconcile_title)
                .setView(view)
                .setPositiveButton(R.string.reconcile_do, (d, w) -> {
                    String p = textOf(place);
                    Long cents = parseCents(textOf(amount));
                    if (p.isEmpty() || cents == null) {
                        Toast.makeText(this, R.string.error_amount, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.saveReconcile(p, cents, settings.getDefaultAccount(),
                            createBooking.isChecked(), this::refresh);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private List<String> placeOptions() {
        List<String> options = new ArrayList<>(placesStore.getPlaces());
        options.add(PlacesStore.NO_PLACE);
        return options;
    }

    private String textOf(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private Long parseCents(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(" ", "").replace(",", ".");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " €";
    }
}
