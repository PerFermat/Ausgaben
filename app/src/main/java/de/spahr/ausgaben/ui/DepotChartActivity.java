package de.spahr.ausgaben.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Kreisdiagramm der Wertpapiere eines Depots (Anteil am Depotwert). Eigene Seite (Menü „Auswertung" im
 * Depot). Die Segmente sind unbeschriftet; ein Tipp zeigt in der Mitte Name + Betrag des Wertpapiers, ohne
 * Auswahl steht dort „Gesamt: <Depotwert>".
 */
public class DepotChartActivity extends LocalizedActivity {

    public static final String EXTRA_DEPOT = "depot";

    /** Farbpalette für die Segmente (kontrastreich, in Hell und Dunkel lesbar). */
    private static final int[] PIE_COLORS = {
            0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726, 0xFFEF5350, 0xFFAB47BC,
            0xFF26C6DA, 0xFFFFCA28, 0xFF8D6E63, 0xFF5C6BC0, 0xFFEC407A,
            0xFF9CCC65, 0xFF29B6F6, 0xFFFF7043, 0xFF78909C, 0xFF7E57C2};

    private PieChart pie;
    private TextView empty;
    private String totalText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depot_chart);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        pie = findViewById(R.id.depotPie);
        empty = findViewById(R.id.depotChartEmpty);

        String depot = getIntent().getStringExtra(EXTRA_DEPOT);
        if (depot != null && !depot.isEmpty()) {
            toolbar.setTitle(depot);
            new Repository(this).getDepotHoldings(depot, this::render);
        }
    }

    private void render(List<Repository.DepotHolding> holdings) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        long total = 0;
        int i = 0;
        for (Repository.DepotHolding h : holdings) {
            if (h.valueCents <= 0 || Math.abs(h.shares) < 1e-6) {
                continue;
            }
            PieEntry e = new PieEntry(h.valueCents / 100f, h.name);
            e.setData(h.valueCents);
            entries.add(e);
            colors.add(PIE_COLORS[i % PIE_COLORS.length]);
            total += h.valueCents;
            i++;
        }
        if (entries.isEmpty()) {
            pie.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        int textColor = getColor(R.color.chart_text);
        totalText = getString(R.string.saldo_total) + ":\n" + money(total);

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setSliceSpace(2f);
        set.setDrawValues(false); // keine Werte/Namen auf den Segmenten
        PieData data = new PieData(set);

        pie.setData(data);
        pie.getDescription().setEnabled(false);
        pie.getLegend().setEnabled(false);
        pie.setDrawEntryLabels(false); // keine Namen an den Segmenten
        pie.setUsePercentValues(false);
        pie.setDrawHoleEnabled(true);
        pie.setHoleColor(0x00000000);
        pie.setHoleRadius(62f);
        pie.setTransparentCircleAlpha(0);
        pie.setDrawCenterText(true);
        pie.setCenterText(totalText);
        pie.setCenterTextSize(16f);
        pie.setCenterTextColor(textColor);
        pie.setRotationEnabled(false);
        pie.setHighlightPerTapEnabled(true);
        pie.setExtraOffsets(8f, 8f, 8f, 8f);
        pie.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                String name = e instanceof PieEntry ? ((PieEntry) e).getLabel() : "";
                long cents = e.getData() instanceof Long ? (Long) e.getData() : 0L;
                pie.setCenterText(name + "\n" + money(cents));
            }

            @Override
            public void onNothingSelected() {
                pie.setCenterText(totalText);
            }
        });
        pie.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        pie.invalidate();
    }

    private String money(long cents) {
        return MoneyFormat.display(cents, Currencies.getDefault());
    }
}
