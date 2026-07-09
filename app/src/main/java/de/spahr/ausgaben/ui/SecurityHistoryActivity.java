package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.SecurityTx;
import de.spahr.ausgaben.settings.Currencies;

/**
 * Vollbild-Historie eines Wertpapiers: im grünen Kopf der Wertpapiername, in der Saldenzeile per Klick
 * durchschaltbar Wert → Nettoeinsatz → Gewinn/Verlust – jeweils nur für dieses Wertpapier. Darunter alle
 * Käufe/Verkäufe/Dividenden.
 */
public class SecurityHistoryActivity extends LocalizedActivity {

    public static final String EXTRA_DEPOT = "depot";
    public static final String EXTRA_KMY_ID = "kmyId";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SECURITY_VALUE = "securityValue";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    private String securityName = "";
    private Repository.DepotMetrics metrics;
    private java.util.List<Integer> saldoModes = new java.util.ArrayList<>();
    private int saldoIndex = 0;

    private TextView saldoLabel;
    private TextView saldoValue;
    private LinearLayout container;

    private java.util.List<SecurityTx> allTx = new java.util.ArrayList<>();
    /** Aktive Bewegungsarten (leer = alle). */
    private final java.util.Set<String> filterActions = new java.util.HashSet<>();
    private Long filterFrom;
    private Long filterTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_history);

        String depot = getIntent().getStringExtra(EXTRA_DEPOT);
        String kmyId = getIntent().getStringExtra(EXTRA_KMY_ID);
        securityName = orEmpty(getIntent().getStringExtra(EXTRA_NAME));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(securityName);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.security_menu);
        toolbar.getMenu().findItem(R.id.action_filter).setTitle(getString(R.string.action_filter));
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_filter) {
                showFilterDialog();
                return true;
            }
            return false;
        });

        saldoLabel = findViewById(R.id.textSaldoLabel);
        saldoValue = findViewById(R.id.textBalance);
        findViewById(R.id.saldoHeader).setOnClickListener(v -> {
            if (!saldoModes.isEmpty()) {
                saldoIndex = (saldoIndex + 1) % saldoModes.size();
                showSaldo();
            }
        });

        container = findViewById(R.id.historyContainer);
        Repository repository = new Repository(this);
        repository.getSecurityMetrics(depot, kmyId, m -> {
            metrics = m;
            saldoModes = DepotSaldo.modes(m);
            if (saldoIndex >= saldoModes.size()) {
                saldoIndex = 0;
            }
            showSaldo();
        });
        repository.getSecurityTransactions(depot, kmyId, txs -> {
            allTx = txs;
            renderTx();
        });
    }

    private void renderTx() {
        container.removeAllViews();
        boolean any = false;
        for (SecurityTx tx : allTx) {
            if (!matchesFilter(tx)) {
                continue;
            }
            any = true;
            container.addView(buildRow(tx));
        }
        if (!any) {
            TextView t = new TextView(this);
            t.setText(allTx.isEmpty() ? R.string.depot_no_history : R.string.depot_no_match);
            t.setPadding(0, 24, 0, 0);
            container.addView(t);
        }
    }

    private void showSaldo() {
        if (metrics == null || saldoModes.isEmpty()) {
            return;
        }
        int mode = saldoModes.get(saldoIndex % saldoModes.size());
        DepotSaldo.apply(this, saldoLabel, saldoValue, metrics, mode, securityName);
    }

    private boolean matchesFilter(SecurityTx tx) {
        if (!filterActions.isEmpty() && !filterActions.contains(tx.action)) {
            return false;
        }
        if (filterFrom != null && tx.date < filterFrom) {
            return false;
        }
        return filterTo == null || tx.date <= filterTo;
    }

    private void showFilterDialog() {
        if (allTx.isEmpty()) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_security_filter, null, false);
        MaterialCheckBox cbBuy = view.findViewById(R.id.cbBuy);
        MaterialCheckBox cbSell = view.findViewById(R.id.cbSell);
        MaterialCheckBox cbDividend = view.findViewById(R.id.cbDividend);
        boolean all = filterActions.isEmpty();
        cbBuy.setChecked(all || filterActions.contains("buy"));
        cbSell.setChecked(all || filterActions.contains("sell"));
        cbDividend.setChecked(all || filterActions.contains("dividend"));

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (SecurityTx tx : allTx) {
            min = Math.min(min, tx.date);
            max = Math.max(max, tx.date);
        }
        RangeSlider slider = view.findViewById(R.id.dateSlider);
        EditText fromField = view.findViewById(R.id.dateFrom);
        EditText toField = view.findViewById(R.id.dateTo);
        final MonthRange range = MonthRange.attach(slider, fromField, toField, min, max, filterFrom, filterTo);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.action_filter)
                .setView(view)
                .setNeutralButton(R.string.filter_reset, (d, w) -> {
                    filterActions.clear();
                    filterFrom = null;
                    filterTo = null;
                    renderTx();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    filterActions.clear();
                    // Alle drei gewählt (oder keine) → keine Aktions-Einschränkung (zeigt auch Ein-/Ausbuchungen).
                    if (!(cbBuy.isChecked() && cbSell.isChecked() && cbDividend.isChecked())) {
                        if (cbBuy.isChecked()) {
                            filterActions.add("buy");
                            filterActions.add("reinvest");
                        }
                        if (cbSell.isChecked()) {
                            filterActions.add("sell");
                        }
                        if (cbDividend.isChecked()) {
                            filterActions.add("dividend");
                        }
                    }
                    filterFrom = range.getFromMillis();
                    filterTo = range.getToMillis();
                    renderTx();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Tabellarische Zeile im Stil der Kontenbewegungen: links Aktion + Datum/Stück, rechts der Betrag (farbig). */
    private View buildRow(SecurityTx tx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padV = dp(10);
        row.setPadding(0, padV, 0, padV);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(this);
        action.setText(actionLabel(tx.action));
        action.setTextSize(16f);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView sub = new TextView(this);
        String subLine = dateFormat.format(new Date(tx.date));
        if (tx.shares != 0) {
            subLine += "  ·  " + shares(tx.shares) + " " + getString(R.string.depot_shares_unit);
        }
        sub.setText(subLine);
        sub.setTextSize(13f);
        sub.setTextColor(getColor(R.color.grey_text));

        left.addView(action);
        left.addView(sub);

        TextView amount = new TextView(this);
        amount.setText(tx.amountCents != 0 ? money(tx.amountCents) : "");
        amount.setTextSize(16f);
        amount.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        amount.setGravity(Gravity.END);
        amount.setTextColor(amountColor(tx.action));

        row.addView(left);
        row.addView(amount);
        return row;
    }

    /** Verkauf = rot, Kauf/Wiederanlage = grün, Dividende (und Ein-/Ausbuchung) = Standardfarbe. */
    private int amountColor(String action) {
        switch (action == null ? "" : action) {
            case "sell":
                return getColor(R.color.expense_red);
            case "buy":
            case "reinvest":
                return getColor(R.color.income_green);
            default:
                return primaryTextColor();
        }
    }

    private int primaryTextColor() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        return getColor(tv.resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String actionLabel(String action) {
        switch (action == null ? "" : action) {
            case "buy":
                return getString(R.string.action_buy);
            case "sell":
                return getString(R.string.action_sell);
            case "dividend":
                return getString(R.string.action_dividend);
            case "add":
                return getString(R.string.action_add);
            case "remove":
                return getString(R.string.action_remove);
            case "reinvest":
                return getString(R.string.action_reinvest);
            default:
                return action == null ? "" : action;
        }
    }

    private String money(long cents) {
        return de.spahr.ausgaben.settings.MoneyFormat.display(cents, Currencies.getDefault());
    }

    private static String shares(double v) {
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

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
