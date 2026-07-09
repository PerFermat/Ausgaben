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
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.PlaceBalance;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;

/** Bestands-Übersicht: Saldo je Ort, „ohne Ort", Gesamt; Umbuchen und Kassensturz; Ort → Verlauf. */
public class BalanceActivity extends LocalizedActivity {

    private Repository repository;
    private PlacesStore placesStore;
    private SettingsStore settings;
    private LinearLayout container;

    /** Konto → Kontosaldo. */
    private final LinkedHashMap<String, Long> accountBalances = new LinkedHashMap<>();
    /** Konto → (Ort → Saldo). */
    private final Map<String, Map<String, Long>> placeBalances = new LinkedHashMap<>();
    private List<String> accountsOrder = new ArrayList<>();
    private List<String> assetAccounts = new ArrayList<>();
    private List<String> liabilityAccounts = new ArrayList<>();
    private final List<String> depotOrder = new ArrayList<>();
    private final Map<String, List<Repository.DepotHolding>> depotHoldings = new LinkedHashMap<>();
    private long total = 0;

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
            repository.getAccountNames(names -> {
                accountsOrder = names;
                repository.getAllBookings(bks -> {
                    accountBalances.clear();
                    for (Booking b : bks) {
                        long s = b.isIncome ? b.amountCents : -b.amountCents;
                        Long prev = accountBalances.get(b.account);
                        accountBalances.put(b.account, (prev == null ? 0L : prev) + s);
                    }
                    repository.getAllPlaceBalances(pb -> {
                        placeBalances.clear();
                        for (PlaceBalance p : pb) {
                            Map<String, Long> m = placeBalances.get(p.account);
                            if (m == null) {
                                m = new LinkedHashMap<>();
                                placeBalances.put(p.account, m);
                            }
                            m.put(p.place, p.balanceCents);
                        }
                        loadGroupsAndDepots();
                    });
                });
            });
        });
    }

    /** Lädt Anlage/Verbindlichkeit-Gruppen und die Depots, dann Neuaufbau. */
    private void loadGroupsAndDepots() {
        repository.getAccountsGrouped(g -> {
            assetAccounts = g.assets;
            liabilityAccounts = g.liabilities;
            repository.getDepots(depots -> {
                depotOrder.clear();
                depotHoldings.clear();
                if (depots.isEmpty()) {
                    render();
                    return;
                }
                final int[] pending = {depots.size()};
                for (String d : depots) {
                    repository.getDepotHoldings(d, h -> {
                        depotOrder.add(d);
                        depotHoldings.put(d, h);
                        if (--pending[0] == 0) {
                            render();
                        }
                    });
                }
            });
        });
    }

    /** Gruppenliste: Anlage-/Verbindlichkeitskonten (mit Überschrift) → Orte → Depots → Gesamt. */
    private void render() {
        container.removeAllViews();
        if (!assetAccounts.isEmpty()) {
            addSectionHeader(getString(R.string.accounts_asset));
            for (String account : assetAccounts) {
                renderAccount(account);
            }
        }
        if (!liabilityAccounts.isEmpty()) {
            addSectionHeader(getString(R.string.accounts_liability));
            for (String account : liabilityAccounts) {
                renderAccount(account);
            }
        }
        long depotTotal = 0;
        if (!depotOrder.isEmpty()) {
            addSectionHeader(getString(R.string.accounts_depot));
        }
        for (String depot : depotOrder) {
            depotTotal += renderDepotSection(depot);
        }
        // Gesamt = Konten-Gesamt + Depotwert.
        addRow(getString(R.string.saldo_total), total + depotTotal, true, false, false, null,
                de.spahr.ausgaben.settings.Currencies.getDefault());
    }

    /** Ein Konto (fett + Saldo) mit seinen Orten (eingerückt) und Trennstrich. */
    private void renderAccount(final String account) {
        final String currency = de.spahr.ausgaben.settings.Currencies.forAccount(account);
        long accBal = accountBalances.containsKey(account) ? accountBalances.get(account) : 0L;
        addRow(account, accBal, true, false, false, null, currency);

        Map<String, Long> pbal = placeBalances.get(account);
        List<String> places = placesStore.getPlaces(account);

        long realSum = 0;
        for (final String place : places) {
            long bal = placeBal(pbal, place);
            realSum += bal;
            addRow(place, bal, false, true, true, v -> openHistory(account, place), currency);
        }
        if (!places.isEmpty()) {
            long rest = accBal - realSum;
            addRow(getString(R.string.no_place), rest, false, true, false, null, currency);
        }
        addDivider();
    }

    /** Depot-Sektion in den Beständen: nur der Depotwert (keine Einzel-Wertpapiere). Liefert den Wert. */
    private long renderDepotSection(String depot) {
        List<Repository.DepotHolding> holdings = depotHoldings.get(depot);
        if (holdings == null) {
            return 0;
        }
        long sub = 0;
        for (Repository.DepotHolding h : holdings) {
            sub += h.valueCents;
        }
        addRow(depot, sub, true, false, false, null, de.spahr.ausgaben.settings.Currencies.getDefault());
        addDivider();
        return sub;
    }

    /** Abschnittsüberschrift im Invers-Schema der Kontenschublade (siehe AccountDrawerAdapter). */
    private void addSectionHeader(String title) {
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        TextView header = new TextView(this);
        header.setText(title);
        header.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        header.setTextSize(15f);
        header.setBackgroundColor(night ? 0xFF808080 : 0xFF303030);
        header.setTextColor(night ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
        int padH = Math.round(16 * getResources().getDisplayMetrics().density);
        header.setPadding(padH, Math.round(10 * getResources().getDisplayMetrics().density),
                padH, Math.round(8 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        header.setLayoutParams(lp);
        container.addView(header);
    }

    private long placeBal(Map<String, Long> map, String place) {
        return map != null && map.containsKey(place) ? map.get(place) : 0L;
    }

    private void openHistory(String account, String place) {
        Intent i = new Intent(this, PlaceHistoryActivity.class);
        i.putExtra(PlaceHistoryActivity.EXTRA_ACCOUNT, account);
        i.putExtra(PlaceHistoryActivity.EXTRA_PLACE, place);
        startActivity(i);
    }

    private void addDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dp.topMargin = 12;
        dp.bottomMargin = 4;
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(getColor(R.color.grey_text));
        container.addView(divider);
    }

    private void addRow(String label, long cents, boolean bold, boolean indent,
                        boolean clickable, View.OnClickListener onClick, String currency) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int lead = indent ? Math.round(24 * getResources().getDisplayMetrics().density) : 0;
        row.setPadding(lead, indent ? 16 : 22, 0, indent ? 16 : 22);
        if (clickable) {
            row.setClickable(true);
            row.setOnClickListener(onClick);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
        }

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(bold ? 17f : 16f);
        name.setTypeface(android.graphics.Typeface.DEFAULT, bold ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText(formatEuro(cents, currency));
        value.setTextSize(bold ? 17f : 16f);
        value.setGravity(Gravity.END);
        value.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        value.setTextColor(cents < 0 ? getColor(R.color.expense_red) : getColor(R.color.income_green));

        row.addView(name);
        row.addView(value);
        container.addView(row);
    }

    // ---- Umbuchen ----

    private void showTransferDialog() {
        if (accountsOrder.isEmpty()) {
            Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_transfer, null, false);
        MaterialAutoCompleteTextView accountField = view.findViewById(R.id.transferAccount);
        MaterialAutoCompleteTextView from = view.findViewById(R.id.transferFrom);
        MaterialAutoCompleteTextView to = view.findViewById(R.id.transferTo);
        TextInputEditText amount = view.findViewById(R.id.transferAmount);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        accountField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountsOrder));
        final String[] account = {startAccount()};
        accountField.setText(account[0], false);
        fillTransferPlaces(from, to, account[0]);
        accountField.setOnItemClickListener((p, v, pos, id) -> {
            account[0] = (String) p.getItemAtPosition(pos);
            fillTransferPlaces(from, to, account[0]);
        });

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
                    repository.saveTransfer(account[0], f, t, cents, this::refresh);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Von/Nach-Dropdowns auf die Orte des Kontos setzen; „Nach" = Standardort des Kontos. */
    private void fillTransferPlaces(MaterialAutoCompleteTextView from, MaterialAutoCompleteTextView to,
                                    String account) {
        List<String> options = placeOptions(account);
        from.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        to.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options));
        String standardort = placesStore.getDefaultPlace(account);
        String toPreset = (!standardort.isEmpty() && options.contains(standardort))
                ? standardort : options.get(options.size() - 1);
        to.setText(toPreset, false);
        // „Von" auf den ersten Ort, der nicht dem „Nach"-Ort entspricht.
        String fromPreset = options.get(0);
        if (fromPreset.equals(toPreset) && options.size() > 1) {
            fromPreset = options.get(1);
        }
        from.setText(fromPreset, false);
    }

    // ---- Kassensturz ----

    private void showReconcileDialog() {
        if (accountsOrder.isEmpty()) {
            Toast.makeText(this, R.string.no_accounts, Toast.LENGTH_LONG).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reconcile, null, false);
        MaterialAutoCompleteTextView accountField = view.findViewById(R.id.reconcileAccount);
        MaterialAutoCompleteTextView place = view.findViewById(R.id.reconcilePlace);
        TextInputEditText amount = view.findViewById(R.id.reconcileAmount);
        com.google.android.material.checkbox.MaterialCheckBox createBooking =
                view.findViewById(R.id.reconcileCreateBooking);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));

        accountField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountsOrder));
        final String[] account = {startAccount()};
        accountField.setText(account[0], false);
        fillReconcilePlaces(place, account[0]);
        accountField.setOnItemClickListener((p, v, pos, id) -> {
            account[0] = (String) p.getItemAtPosition(pos);
            fillReconcilePlaces(place, account[0]);
        });

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
                    repository.saveReconcile(account[0], p, cents, createBooking.isChecked(), this::refresh);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void fillReconcilePlaces(MaterialAutoCompleteTextView place, String account) {
        List<String> places = placesStore.getPlaces(account);
        place.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, places));
        place.setText(places.isEmpty() ? "" : places.get(0), false);
    }

    /** Standardkonto, sonst das erste vorhandene Konto. */
    private String startAccount() {
        String def = settings.getDefaultAccount();
        if (!def.isEmpty() && accountsOrder.contains(def)) {
            return def;
        }
        return accountsOrder.get(0);
    }

    private List<String> placeOptions(String account) {
        List<String> options = new ArrayList<>(placesStore.getPlaces(account));
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

    private String formatEuro(long signedCents, String currency) {
        return de.spahr.ausgaben.settings.MoneyFormat.display(signedCents, currency);
    }
}
