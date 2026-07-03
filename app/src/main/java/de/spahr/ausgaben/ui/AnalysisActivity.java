package de.spahr.ausgaben.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.PlaceEntry;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.PlacesStore;

public class AnalysisActivity extends AppCompatActivity {

    public static final String EXTRA_FILTER_PAYEE = "filter_payee";
    public static final String EXTRA_FILTER_CATEGORY = "filter_category";
    public static final String EXTRA_FILTER_CATEGORY_MAIN = "filter_category_main";
    public static final String EXTRA_FILTER_AMOUNT_FROM = "filter_amount_from";
    public static final String EXTRA_FILTER_AMOUNT_TO = "filter_amount_to";
    public static final String EXTRA_VIEW_KEY = "view_key";

    private enum Granularity {DAY, WEEK, MONTH, YEAR}

    private static final int DEFAULT_BARS = 12;

    private Repository repository;
    private CombinedChart chart;
    private TextView textTotal;
    private FloatingActionButton fabScrollRight;
    private MaterialAutoCompleteTextView viewSelector;

    private List<Booking> allBookings = new ArrayList<>();
    private List<PlaceEntry> allPlaceEntries = new ArrayList<>();
    private boolean bookingsLoaded = false;
    private boolean placesLoaded = false;

    private Granularity granularity = Granularity.MONTH;
    private int lastIndex = 0;

    private String filterPayee = "";
    private String filterCategory = "";
    private boolean filterCategoryIsMain = false;
    private Long filterAmountFrom = null;
    private Long filterAmountTo = null;

    private String defaultAccount = "";
    /** Trennt Konto und Ort im View-Key (nicht in Namen enthalten). */
    private static final String PLACE_SEP = "\u001f";

    private final List<String> viewKeys = new ArrayList<>();
    private final List<String> viewLabels = new ArrayList<>();
    private String viewKey = MainActivity.VIEW_TOTAL;

    /** Kategorie-Teile je Buchung (Splitbuchungen), damit der Kategorie-Filter alle Teile berücksichtigt. */
    private java.util.Map<Long, List<de.spahr.ausgaben.db.BookingSplit>> splitsByBooking =
            new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        filterPayee = orEmpty(getIntent().getStringExtra(EXTRA_FILTER_PAYEE));
        filterCategory = orEmpty(getIntent().getStringExtra(EXTRA_FILTER_CATEGORY));
        filterCategoryIsMain = getIntent().getBooleanExtra(EXTRA_FILTER_CATEGORY_MAIN, false);
        long from = getIntent().getLongExtra(EXTRA_FILTER_AMOUNT_FROM, Long.MIN_VALUE);
        long to = getIntent().getLongExtra(EXTRA_FILTER_AMOUNT_TO, Long.MAX_VALUE);
        filterAmountFrom = from == Long.MIN_VALUE ? null : from;
        filterAmountTo = to == Long.MAX_VALUE ? null : to;
        viewKey = orEmpty(getIntent().getStringExtra(EXTRA_VIEW_KEY));
        if (viewKey.isEmpty()) {
            viewKey = MainActivity.VIEW_TOTAL;
        }

        repository = new Repository(this);
        defaultAccount = new de.spahr.ausgaben.settings.SettingsStore(this).getDefaultAccount();
        chart = findViewById(R.id.barChart);
        textTotal = findViewById(R.id.textTotal);
        fabScrollRight = findViewById(R.id.fabScrollRight);
        viewSelector = findViewById(R.id.viewSelector);

        setupChart();

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleGranularity);
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

        fabScrollRight.setOnClickListener(v -> {
            chart.moveViewToX(lastIndex);
            updateScrollRightVisibility();
        });

        repository.getAllBookings(result -> {
            allBookings = result;
            repository.getAllSplitsMap(m -> {
                splitsByBooking = m;
                bookingsLoaded = true;
                setupViewSelector();
                if (placesLoaded) renderChart();
            });
        });
        repository.getAllPlaceEntries(result -> {
            allPlaceEntries = result;
            placesLoaded = true;
            if (bookingsLoaded) renderChart();
        });
    }

    private void setupViewSelector() {
        viewKeys.clear();
        viewLabels.clear();
        // Gesamt (alle Konten)
        viewKeys.add(MainActivity.VIEW_TOTAL);
        viewLabels.add(getString(R.string.saldo_total));
        // Jedes Konto (aus den vorhandenen Buchungen, stabile Reihenfolge)
        java.util.LinkedHashSet<String> accounts = new java.util.LinkedHashSet<>();
        for (Booking b : allBookings) {
            if (b.account != null && !b.account.isEmpty()) {
                accounts.add(b.account);
            }
        }
        for (String acc : accounts) {
            viewKeys.add(MainActivity.VIEW_ACCOUNT_PREFIX + acc);
            viewLabels.add(acc);
        }
        // Orte je Konto (Standardort = Residual → als „ohne Ort" je Konto separat).
        PlacesStore ps = new PlacesStore(this);
        for (String acc : accounts) {
            String def = ps.getDefaultPlace(acc);
            List<String> pl = ps.getPlaces(acc);
            for (String place : pl) {
                if (!place.equals(def)) {
                    viewKeys.add(MainActivity.VIEW_PLACE_PREFIX + acc + PLACE_SEP + place);
                    viewLabels.add(acc + " · " + place);
                }
            }
            if (!pl.isEmpty()) {
                viewKeys.add(MainActivity.VIEW_NOPLACE + PLACE_SEP + acc);
                viewLabels.add(acc + " · " + getString(R.string.no_place));
            }
        }
        if (isFilterActive()) {
            viewKeys.add(MainActivity.VIEW_FILTERED);
            viewLabels.add(getString(R.string.saldo_filtered));
        }
        if (!viewKeys.contains(viewKey)) {
            viewKey = MainActivity.VIEW_TOTAL;
        }
        viewSelector.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, viewLabels));
        viewSelector.setText(viewLabels.get(viewKeys.indexOf(viewKey)), false);
        viewSelector.setOnItemClickListener((parent, view, position, id) -> {
            viewKey = viewKeys.get(position);
            renderChart();
        });
    }

    private boolean isFilterActive() {
        return !filterPayee.isEmpty() || !filterCategory.isEmpty()
                || filterAmountFrom != null || filterAmountTo != null;
    }

    // ---- Ereignisstrom je Sicht (Zeit, vorzeichenbehaftete Cent) ----

    private List<long[]> eventsForView() {
        List<long[]> events = new ArrayList<>();
        if (viewKey.startsWith(MainActivity.VIEW_ACCOUNT_PREFIX)) {
            String account = viewKey.substring(MainActivity.VIEW_ACCOUNT_PREFIX.length());
            for (Booking b : allBookings) {
                if (b.account.equalsIgnoreCase(account)) {
                    events.add(new long[]{b.createdAt, b.isIncome ? b.amountCents : -b.amountCents});
                }
            }
        } else if (viewKey.startsWith(MainActivity.VIEW_PLACE_PREFIX)) {
            String rest = viewKey.substring(MainActivity.VIEW_PLACE_PREFIX.length());
            int i = rest.indexOf(PLACE_SEP);
            String acc = i < 0 ? "" : rest.substring(0, i);
            String place = i < 0 ? rest : rest.substring(i + PLACE_SEP.length());
            for (PlaceEntry e : allPlaceEntries) {
                if (e.account.equalsIgnoreCase(acc) && e.place.equals(place)) {
                    events.add(new long[]{e.createdAt, e.amountCents});
                }
            }
        } else if (viewKey.startsWith(MainActivity.VIEW_NOPLACE)) {
            // „ohne Ort" eines Kontos = dessen Buchungen − dessen Ort-Bewegungen (Rest/Standardort).
            String acc = viewKey.length() > MainActivity.VIEW_NOPLACE.length()
                    ? viewKey.substring(MainActivity.VIEW_NOPLACE.length() + PLACE_SEP.length())
                    : defaultAccount;
            for (Booking b : allBookings) {
                if (acc.isEmpty() || b.account.equalsIgnoreCase(acc)) {
                    events.add(new long[]{b.createdAt, b.isIncome ? b.amountCents : -b.amountCents});
                }
            }
            for (PlaceEntry e : allPlaceEntries) {
                if (acc.isEmpty() || e.account.equalsIgnoreCase(acc)) {
                    events.add(new long[]{e.createdAt, -e.amountCents});
                }
            }
        } else { // TOTAL oder FILTERED (über Buchungen)
            boolean onlyFiltered = viewKey.equals(MainActivity.VIEW_FILTERED);
            for (Booking b : allBookings) {
                if (onlyFiltered && !matchesFilter(b)) {
                    continue;
                }
                long signed = b.isIncome ? b.amountCents : -b.amountCents;
                // Gefilterte Sicht mit Kategorie-Filter: Splitbuchung nur mit dem passenden Teilbetrag.
                if (onlyFiltered) {
                    signed = displaySignedForFilter(b, signed);
                }
                events.add(new long[]{b.createdAt, signed});
            }
        }
        Collections.sort(events, Comparator.comparingLong(a -> a[0]));
        return events;
    }

    private boolean matchesFilter(Booking b) {
        if (!filterPayee.isEmpty()
                && !b.payee.toLowerCase(Locale.GERMANY).contains(filterPayee.toLowerCase(Locale.GERMANY))) {
            return false;
        }
        if (!filterCategory.isEmpty() && !categoryMatchesBooking(b)) {
            return false;
        }
        if (filterAmountFrom != null && b.amountCents < filterAmountFrom) {
            return false;
        }
        return filterAmountTo == null || b.amountCents <= filterAmountTo;
    }

    /** Bei Kategorie-Filter nur den Teilbetrag der gewählten Kategorie einer Splitbuchung; sonst {@code full}. */
    private long displaySignedForFilter(Booking b, long full) {
        if (filterCategory.isEmpty()) {
            return full;
        }
        List<de.spahr.ausgaben.db.BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts == null || parts.isEmpty()) {
            return full;
        }
        long sum = 0;
        boolean any = false;
        for (de.spahr.ausgaben.db.BookingSplit p : parts) {
            if (categoryMatches(p.category)) {
                sum += b.isIncome ? p.amountCents : -p.amountCents;
                any = true;
            }
        }
        return any ? sum : full;
    }

    /** Treffer, wenn die (Haupt-)Kategorie oder eine Teilkategorie einer Splitbuchung passt. */
    private boolean categoryMatchesBooking(Booking b) {
        if (categoryMatches(b.category)) {
            return true;
        }
        List<de.spahr.ausgaben.db.BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts != null) {
            for (de.spahr.ausgaben.db.BookingSplit p : parts) {
                if (categoryMatches(p.category)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean categoryMatches(String cat) {
        if (cat == null) {
            return false;
        }
        if (filterCategoryIsMain) {
            return cat.equalsIgnoreCase(filterCategory)
                    || cat.toLowerCase(Locale.GERMANY).startsWith(
                            filterCategory.toLowerCase(Locale.GERMANY) + ":");
        }
        return cat.equalsIgnoreCase(filterCategory);
    }

    private void renderChart() {
        List<long[]> events = eventsForView();

        long total = 0;
        for (long[] e : events) {
            total += e[1];
        }
        textTotal.setText(getString(R.string.analysis_total, formatEuro(total)));

        if (events.isEmpty()) {
            chart.clear();
            chart.invalidate();
            fabScrollRight.hide();
            return;
        }

        // Netto + Vorhandensein je Periode
        Map<Long, Long> netByPeriod = new HashMap<>();
        Set<Long> hasEvent = new HashSet<>();
        long minMs = Long.MAX_VALUE;
        long maxMs = Long.MIN_VALUE;
        for (long[] e : events) {
            long ps = periodStart(e[0]);
            Long curVal = netByPeriod.get(ps);
            netByPeriod.put(ps, (curVal == null ? 0L : curVal) + e[1]);
            hasEvent.add(ps);
            if (ps < minMs) minMs = ps;
            if (ps > maxMs) maxMs = ps;
        }

        List<Long> periods = new ArrayList<>();
        Calendar cur = Calendar.getInstance();
        cur.setTimeInMillis(minMs);
        while (cur.getTimeInMillis() <= maxMs && periods.size() <= 5000) {
            periods.add(cur.getTimeInMillis());
            advance(cur);
        }

        List<BarEntry> barEntries = new ArrayList<>();
        List<Integer> barColors = new ArrayList<>();
        List<Entry> lineEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int green = getColor(R.color.income_green);
        int red = getColor(R.color.expense_red);
        long running = 0;
        for (int i = 0; i < periods.size(); i++) {
            long ps = periods.get(i);
            labels.add(label(ps));
            Long net = netByPeriod.get(ps);
            long netVal = net == null ? 0L : net;
            running += netVal;
            if (hasEvent.contains(ps)) {
                barEntries.add(new BarEntry(i, netVal / 100f));
                barColors.add(netVal >= 0 ? green : red);
            }
            lineEntries.add(new Entry(i, running / 100f));
        }
        lastIndex = periods.size() - 1;

        int chartText = getColor(R.color.chart_text);

        BarDataSet barSet = new BarDataSet(barEntries, "");
        barSet.setColors(barColors);
        barSet.setValueTextColor(chartText);
        barSet.setValueTextSize(11f);
        BarData barData = new BarData(barSet);
        barData.setBarWidth(0.6f);

        LineDataSet lineSet = new LineDataSet(lineEntries, "");
        int lineColor = getColor(R.color.chart_line);
        lineSet.setColor(lineColor);
        lineSet.setLineWidth(2.2f);
        lineSet.setDrawCircles(false);
        lineSet.setDrawValues(false);
        lineSet.setMode(LineDataSet.Mode.LINEAR);
        LineData lineData = new LineData(lineSet);

        CombinedData combined = new CombinedData();
        combined.setData(barData);
        combined.setData(lineData);
        chart.setData(combined);

        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(periods.size() - 0.5f);
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setLabelCount(landscape ? 12 : 6, false);

        // X frei zoombar (Balkenanzahl per Geste), Anfangsansicht ≈ DEFAULT_BARS; Y frei zoombar.
        chart.setVisibleXRangeMinimum(2f);
        chart.setVisibleXRangeMaximum(Math.max(2f, periods.size()));
        chart.fitScreen();
        if (periods.size() > DEFAULT_BARS) {
            chart.zoom((float) periods.size() / DEFAULT_BARS, 1f, 0f, 0f);
        }
        chart.moveViewToX(lastIndex);
        chart.invalidate();
        updateScrollRightVisibility();
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        // Zoom per Fingergeste: horizontal = Balkenanzahl (X), vertikal = Y-Achse. Unabhängig (kein Pinch-Both).
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(true);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDragEnabled(true);
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE});

        int chartText = getColor(R.color.chart_text);
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setAvoidFirstLastClipping(true);
        x.setTextColor(chartText);
        x.setTextSize(12f);
        chart.getAxisLeft().setTextColor(chartText);
        chart.getAxisLeft().setTextSize(12f);

        chart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture g) { }
            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture g) {
                updateScrollRightVisibility();
            }
            @Override public void onChartLongPressed(MotionEvent me) { }
            @Override public void onChartDoubleTapped(MotionEvent me) { }
            @Override public void onChartSingleTapped(MotionEvent me) { }
            @Override public void onChartFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { }
            @Override public void onChartScale(MotionEvent me, float sx, float sy) { }
            @Override public void onChartTranslate(MotionEvent me, float dx, float dy) {
                updateScrollRightVisibility();
            }
        });
    }

    private void updateScrollRightVisibility() {
        chart.post(() -> {
            if (chart.getData() == null) {
                fabScrollRight.hide();
                return;
            }
            boolean atRight = chart.getHighestVisibleX() >= lastIndex - 0.5f;
            if (atRight) {
                fabScrollRight.hide();
            } else {
                fabScrollRight.show();
            }
        });
    }

    // ---- Perioden-Hilfen ----

    private long periodStart(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        switch (granularity) {
            case WEEK:
                c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                break;
            case MONTH:
                c.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case YEAR:
                c.set(Calendar.DAY_OF_YEAR, 1);
                break;
            case DAY:
            default:
                break;
        }
        return c.getTimeInMillis();
    }

    private void advance(Calendar c) {
        switch (granularity) {
            case WEEK:
                c.add(Calendar.DAY_OF_MONTH, 7);
                break;
            case MONTH:
                c.add(Calendar.MONTH, 1);
                break;
            case YEAR:
                c.add(Calendar.YEAR, 1);
                break;
            case DAY:
            default:
                c.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }
    }

    private String label(long periodStartMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(periodStartMs);
        switch (granularity) {
            case DAY:
                return new SimpleDateFormat("dd.MM.", Locale.GERMANY).format(new Date(periodStartMs));
            case WEEK:
                return String.format(Locale.GERMANY, "%02d/%02d",
                        c.get(Calendar.WEEK_OF_YEAR), c.get(Calendar.YEAR) % 100);
            case YEAR:
                return String.valueOf(c.get(Calendar.YEAR));
            case MONTH:
            default:
                return String.format(Locale.GERMANY, "%02d/%02d",
                        c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100);
        }
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " €";
    }
}
