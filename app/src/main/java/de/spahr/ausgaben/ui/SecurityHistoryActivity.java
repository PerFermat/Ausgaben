package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.SecurityTx;
import de.spahr.ausgaben.settings.Currencies;

/**
 * Vollbild-Historie eines Wertpapiers: im grünen Kopf der Wertpapiername, in der Saldenzeile per Klick
 * umschaltbar zwischen Depotwert und dem Wert dieses Wertpapiers, darunter alle Käufe/Verkäufe/Dividenden.
 */
public class SecurityHistoryActivity extends LocalizedActivity {

    public static final String EXTRA_DEPOT = "depot";
    public static final String EXTRA_KMY_ID = "kmyId";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SECURITY_VALUE = "securityValue";
    public static final String EXTRA_DEPOT_VALUE = "depotValue";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    private String securityName = "";
    private long securityValueCents = 0;
    private long depotValueCents = 0;
    /** 0 = Depotwert, 1 = Wert dieses Wertpapiers. */
    private int saldoMode = 0;

    private TextView saldoLabel;
    private TextView saldoValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_history);

        String depot = getIntent().getStringExtra(EXTRA_DEPOT);
        String kmyId = getIntent().getStringExtra(EXTRA_KMY_ID);
        securityName = orEmpty(getIntent().getStringExtra(EXTRA_NAME));
        securityValueCents = getIntent().getLongExtra(EXTRA_SECURITY_VALUE, 0);
        depotValueCents = getIntent().getLongExtra(EXTRA_DEPOT_VALUE, 0);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(securityName);
        toolbar.setNavigationOnClickListener(v -> finish());

        saldoLabel = findViewById(R.id.textSaldoLabel);
        saldoValue = findViewById(R.id.textBalance);
        findViewById(R.id.saldoHeader).setOnClickListener(v -> {
            saldoMode = 1 - saldoMode;
            showSaldo();
        });
        showSaldo();

        LinearLayout container = findViewById(R.id.historyContainer);
        Repository repository = new Repository(this);
        repository.getSecurityTransactions(depot, kmyId, txs -> {
            container.removeAllViews();
            if (txs.isEmpty()) {
                TextView t = new TextView(this);
                t.setText(R.string.depot_no_history);
                t.setPadding(0, 24, 0, 0);
                container.addView(t);
                return;
            }
            for (SecurityTx tx : txs) {
                container.addView(buildRow(tx));
            }
        });
    }

    private void showSaldo() {
        boolean depotMode = saldoMode == 0;
        saldoLabel.setText(depotMode ? getString(R.string.depot_value_label) : securityName);
        long cents = depotMode ? depotValueCents : securityValueCents;
        saldoValue.setText(money(cents));
        saldoValue.setTextColor(cents < 0 ? getColor(R.color.expense_red) : getColor(R.color.income_green));
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
        long euros = cents / 100;
        long c = Math.abs(cents % 100);
        String sign = (cents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", c) + " " + Currencies.getDefault();
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
