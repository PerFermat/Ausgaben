package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.Currencies;

/**
 * Depot-Ansicht: je Wertpapier Stückzahl × letzter Kurs = aktueller Wert, dazu der Depot-Gesamtwert.
 * Klick auf ein Wertpapier zeigt seine Käufe/Verkäufe/Dividenden. Getrennt von den Konto-Salden.
 */
public class DepotActivity extends LocalizedActivity {

    /** Optional: nur dieses Depot anzeigen (aus der Kontenschublade). Leer = alle Depots. */
    public static final String EXTRA_DEPOT = "depot";

    private Repository repository;
    private LinearLayout container;
    private TextView totalView;
    private String onlyDepot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depot);
        repository = new Repository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        container = findViewById(R.id.depotContainer);
        totalView = findViewById(R.id.depotTotal);
        onlyDepot = getIntent().getStringExtra(EXTRA_DEPOT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        container.removeAllViews();
        totalView.setText(getString(R.string.depot_value_total, money(0)));
        if (onlyDepot != null && !onlyDepot.isEmpty()) {
            renderDepots(Collections.singletonList(onlyDepot));
            return;
        }
        repository.getDepots(this::renderDepots);
    }

    private void renderDepots(List<String> depots) {
        if (depots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.depot_empty);
            empty.setPadding(0, 24, 0, 0);
            container.addView(empty);
            return;
        }
        final long[] grandTotal = {0};
        final int[] pending = {depots.size()};
        for (String depot : depots) {
            repository.getDepotHoldings(depot, holdings -> {
                long sub = addDepotSection(depot, depots.size() > 1, holdings);
                grandTotal[0] += sub;
                if (--pending[0] == 0) {
                    totalView.setText(getString(R.string.depot_value_total, money(grandTotal[0])));
                }
            });
        }
    }

    /** Fügt eine Depot-Sektion hinzu und liefert deren Gesamtwert (Cent). Verkaufte Wertpapiere (0 Stück) werden ausgeblendet. */
    private long addDepotSection(String depot, boolean showHeader, List<Repository.DepotHolding> holdings) {
        long sub = 0;
        for (Repository.DepotHolding h : holdings) {
            sub += h.valueCents;
        }
        final long depotValue = sub;
        if (showHeader) {
            addRow(depot, money(sub), true, false, null);
        }
        for (Repository.DepotHolding h : holdings) {
            if (Math.abs(h.shares) < 1e-6) {
                continue; // vollständig verkauft → nicht anzeigen
            }
            String left = h.name + (h.symbol.isEmpty() ? "" : "  ·  " + h.symbol)
                    + "\n" + shares(h.shares) + " × " + price(h.price);
            addRow(left, money(h.valueCents), false, true, v -> openHistory(depot, h, depotValue));
        }
        return sub;
    }

    /** Öffnet die Vollbild-Historie des Wertpapiers. */
    private void openHistory(String depot, Repository.DepotHolding h, long depotValue) {
        Intent i = new Intent(this, SecurityHistoryActivity.class);
        i.putExtra(SecurityHistoryActivity.EXTRA_DEPOT, depot);
        i.putExtra(SecurityHistoryActivity.EXTRA_KMY_ID, h.kmyId);
        i.putExtra(SecurityHistoryActivity.EXTRA_NAME, h.name);
        i.putExtra(SecurityHistoryActivity.EXTRA_SECURITY_VALUE, h.valueCents);
        i.putExtra(SecurityHistoryActivity.EXTRA_DEPOT_VALUE, depotValue);
        startActivity(i);
    }

    private void addRow(String label, String value, boolean bold, boolean clickable,
                        View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, bold ? 20 : 14, 0, bold ? 12 : 14);
        if (clickable) {
            row.setClickable(true);
            row.setOnClickListener(onClick);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
        }
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(bold ? 17f : 15f);
        name.setTypeface(android.graphics.Typeface.DEFAULT, bold ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(bold ? 17f : 15f);
        val.setGravity(Gravity.END);
        val.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);

        row.addView(name);
        row.addView(val);
        container.addView(row);
    }

    private String money(long cents) {
        long euros = cents / 100;
        long c = Math.abs(cents % 100);
        String sign = (cents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", c) + " " + Currencies.getDefault();
    }

    /** Stückzahl mit bis zu 4 Nachkommastellen, ohne überflüssige Nullen. */
    private static String shares(double v) {
        return trim(String.format(Locale.GERMANY, "%.4f", v));
    }

    private static String price(double v) {
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
}
