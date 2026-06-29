package de.spahr.ausgaben.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;

public class AnalysisActivity extends AppCompatActivity {

    private enum Granularity {DAY, WEEK, MONTH, YEAR}

    /** Wie viele Perioden (Balken) maximal angezeigt werden. */
    private static final int MAX_BUCKETS = 12;

    private Repository repository;
    private BarChart chart;
    private android.widget.TextView textTotal;
    private MaterialButtonToggleGroup toggle;

    private List<Booking> bookings = new ArrayList<>();
    private Granularity granularity = Granularity.MONTH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        chart = findViewById(R.id.barChart);
        textTotal = findViewById(R.id.textTotal);
        toggle = findViewById(R.id.toggleGranularity);

        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setFitBars(true);
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);

        toggle.check(R.id.btnMonth);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnDay) {
                granularity = Granularity.DAY;
            } else if (checkedId == R.id.btnWeek) {
                granularity = Granularity.WEEK;
            } else if (checkedId == R.id.btnMonth) {
                granularity = Granularity.MONTH;
            } else if (checkedId == R.id.btnYear) {
                granularity = Granularity.YEAR;
            }
            renderChart();
        });

        repository.getAllBookings(result -> {
            bookings = result;
            renderChart();
        });
    }

    private void renderChart() {
        // Netto je Periode (Einnahmen − Ausgaben), chronologisch.
        Map<String, Long> buckets = new LinkedHashMap<>();
        // chronologisch sortieren: getAllBookings liefert absteigend -> rückwärts iterieren
        for (int i = bookings.size() - 1; i >= 0; i--) {
            Booking b = bookings.get(i);
            String key = bucketKey(b.createdAt);
            long signed = b.isIncome ? b.amountCents : -b.amountCents;
            Long cur = buckets.get(key);
            buckets.put(key, (cur == null ? 0L : cur) + signed);
        }

        List<String> keys = new ArrayList<>(buckets.keySet());
        // nur die letzten MAX_BUCKETS Perioden
        int from = Math.max(0, keys.size() - MAX_BUCKETS);
        keys = keys.subList(from, keys.size());

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        long totalCents = 0;
        int green = getColor(R.color.income_green);
        int red = getColor(R.color.expense_red);
        for (int i = 0; i < keys.size(); i++) {
            long cents = buckets.get(keys.get(i));
            totalCents += cents;
            entries.add(new BarEntry(i, cents / 100f));
            colors.add(cents >= 0 ? green : red);
            labels.add(keys.get(i));
        }

        // Gesamtbetrag über ALLE Buchungen (nicht nur sichtbare Balken)
        long overall = 0;
        for (Booking b : bookings) {
            overall += b.isIncome ? b.amountCents : -b.amountCents;
        }
        textTotal.setText(getString(R.string.analysis_total, formatEuro(overall)));

        if (entries.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }

        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(colors);
        set.setValueTextSize(9f);
        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chart.setData(data);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setLabelCount(labels.size());
        chart.setVisibleXRangeMaximum(MAX_BUCKETS);
        chart.invalidate();
        chart.animateY(400);
    }

    private String bucketKey(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        switch (granularity) {
            case DAY:
                return new SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(new Date(millis));
            case WEEK:
                return "KW" + c.get(Calendar.WEEK_OF_YEAR) + "/" + (c.get(Calendar.YEAR) % 100);
            case YEAR:
                return String.valueOf(c.get(Calendar.YEAR));
            case MONTH:
            default:
                return new SimpleDateFormat("MM.yy", Locale.GERMANY).format(new Date(millis));
        }
    }

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " €";
    }
}
