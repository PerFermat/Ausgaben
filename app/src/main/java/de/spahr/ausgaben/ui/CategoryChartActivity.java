package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.CategorySum;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * „Wofür geht mein Geld?" – die <b>Ausgaben je Kategorie</b> für einen Zeitraum als Kreisdiagramm plus
 * absteigend sortierte Liste. Ergänzt die zeitliche Auswertung ({@link AnalysisActivity}), die nur nach
 * Konto/Ort/Gesamt über die Zeit aggregiert.
 *
 * <p>Datenbasis ist {@code BookingDao.getCategoryActuals} (dieselbe Quelle wie die Budget-Seite): sie
 * berücksichtigt Splitbuchungen über ihre Teilbeträge, lässt Umbuchungen außen vor und gilt über
 * <b>alle Konten</b>. Gezeigt werden nur Kategorien mit Netto-<b>Abfluss</b>.</p>
 */
public class CategoryChartActivity extends LocalizedActivity {

    /** Farbpalette wie im Depot-Kreisdiagramm (kontrastreich, in Hell und Dunkel lesbar). */
    private static final int[] PIE_COLORS = {
            0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726, 0xFFEF5350, 0xFFAB47BC,
            0xFF26C6DA, 0xFFFFCA28, 0xFF8D6E63, 0xFF5C6BC0, 0xFFEC407A,
            0xFF9CCC65, 0xFF29B6F6, 0xFFFF7043, 0xFF78909C, 0xFF7E57C2};

    private Repository repository;
    private PieChart pie;
    private LinearLayout list;
    private TextView empty;
    private String totalText = "";
    /** true = laufender Monat, false = laufendes Jahr. */
    private boolean monthRange = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_chart);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        pie = findViewById(R.id.categoryPie);
        list = findViewById(R.id.categoryList);
        empty = findViewById(R.id.categoryEmpty);

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleRange);
        toggle.check(R.id.btnRangeMonth);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                monthRange = checkedId == R.id.btnRangeMonth;
                load();
            }
        });
        load();
    }

    private void load() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (!monthRange) {
            c.set(Calendar.MONTH, Calendar.JANUARY);
        }
        long from = c.getTimeInMillis();
        c.add(monthRange ? Calendar.MONTH : Calendar.YEAR, 1);
        long to = c.getTimeInMillis();
        repository.getCategoryActuals(from, to, this::render);
    }

    /** Eine Kategorie mit positivem Ausgabebetrag (Anzeige-Modell; {@link CategorySum} ist ein Room-Typ). */
    private static final class Slice {
        final String category;
        final long amount;

        Slice(String category, long amount) {
            this.category = category;
            this.amount = amount;
        }
    }

    private void render(List<CategorySum> sums) {
        // Nur Abflüsse (negative Netto-Summe) – als positive Beträge, absteigend.
        List<Slice> out = new ArrayList<>();
        long total = 0;
        for (CategorySum s : sums) {
            if (s.total < 0) {
                out.add(new Slice(s.category, -s.total));
                total += -s.total;
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.amount, a.amount));

        list.removeAllViews();
        if (out.isEmpty()) {
            pie.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        buildPie(out, total);
        for (int i = 0; i < out.size(); i++) {
            list.addView(buildRow(out.get(i), total, PIE_COLORS[i % PIE_COLORS.length]));
        }
        pie.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
    }

    private void buildPie(List<Slice> out, long total) {
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            PieEntry e = new PieEntry(out.get(i).amount / 100f, out.get(i).category);
            e.setData(out.get(i).amount);
            entries.add(e);
            colors.add(PIE_COLORS[i % PIE_COLORS.length]);
        }
        totalText = getString(R.string.saldo_total) + ":\n" + money(total);

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setSliceSpace(2f);
        set.setDrawValues(false);

        pie.setData(new PieData(set));
        pie.getDescription().setEnabled(false);
        pie.getLegend().setEnabled(false);
        pie.setDrawEntryLabels(false);
        pie.setUsePercentValues(false);
        pie.setDrawHoleEnabled(true);
        pie.setHoleColor(0x00000000);
        pie.setHoleRadius(62f);
        pie.setTransparentCircleAlpha(0);
        pie.setDrawCenterText(true);
        pie.setCenterText(totalText);
        pie.setCenterTextSize(16f);
        pie.setCenterTextColor(getColor(R.color.chart_text));
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
        pie.invalidate();
    }

    /** Listenzeile: Farbpunkt · Kategorie · Anteil in Prozent · Betrag (rechts). */
    private View buildRow(Slice s, long total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        View dot = new View(this);
        dot.setBackgroundColor(color);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.setMarginEnd(dp(10));
        row.addView(dot, dotLp);

        TextView name = new TextView(this);
        name.setText(s.category);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView pct = new TextView(this);
        pct.setText(total > 0 ? Math.round(100.0 * s.amount / total) + " %" : "");
        pct.setTextSize(12);
        pct.setTextColor(0xFF9E9E9E);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pctLp.setMarginEnd(dp(10));
        row.addView(pct, pctLp);

        TextView amount = new TextView(this);
        amount.setText(money(s.amount));
        amount.setGravity(Gravity.END);
        amount.setTypeface(amount.getTypeface(), Typeface.BOLD);
        row.addView(amount, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private String money(long cents) {
        return MoneyFormat.display(cents, Currencies.getDefault());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
