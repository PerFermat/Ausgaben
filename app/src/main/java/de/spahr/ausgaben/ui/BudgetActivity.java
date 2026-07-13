package de.spahr.ausgaben.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Budget;
import de.spahr.ausgaben.db.CategorySum;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Budgetplanung: je Kategorie Ist vs. Soll mit farbigem Fortschrittsbalken (grün = im Plan, rot = daneben);
 * umschaltbar Jahres-/Monatssicht und nur Haupt- / mit Unterkategorien. Soll stammt aus KMyMoney (read-only)
 * oder wird app-intern aus dem Verlauf berechnet und ist dann manuell editierbar.
 */
public class BudgetActivity extends LocalizedActivity {

    private Repository repository;
    private SettingsStore settings;
    private LinearLayout container;

    private int year;
    private boolean monthView = false;      // false = Jahr, true = Monat
    private boolean includeSubs = false;    // false = nur Haupt, true = mit Unter
    private int monthOffset = 0;            // Monatssicht: 0 = aktueller Monat, ±n = vor/zurück (Wischgeste)
    private int displayYear;                // Jahr des aktuell angezeigten Zeitraums (folgt monthOffset)
    private int displayMonth;               // 1–12 = angezeigter Monat (Monatssicht), 0 = Jahressicht

    private final List<CategorySum> actuals = new ArrayList<>();
    /** Ist-Summen der letzten 2 Jahre – für Kategorie-Typ und Aktivitätsfilter. */
    private final List<CategorySum> recentActuals = new ArrayList<>();
    private String budgetSource;            // null = kein Budget vorhanden
    private final List<Budget> budgetLines = new ArrayList<>();
    /** Kategorie-Pfad → Typ ({@code true} = Einnahme) aus der Datei; verlässliche Einordnung. */
    private final Map<String, Boolean> categoryTypes = new java.util.HashMap<>();
    private double elapsed = 0;
    private GestureDetector swipeDetector;   // Monats-Wischgeste, ganzflächig via dispatchTouchEvent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        settings = new SettingsStore(this);
        container = findViewById(R.id.budgetContainer);

        year = Calendar.getInstance().get(Calendar.YEAR);

        MaterialButtonToggleGroup toggleView = findViewById(R.id.toggleView);
        toggleView.check(R.id.btnYearView);
        toggleView.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            monthView = checkedId == R.id.btnMonthView;
            monthOffset = 0;   // Monatssicht startet immer beim aktuellen Monat
            reload();
        });

        // Monatssicht: Wischen nach rechts = Monat davor, nach links = Monat danach.
        // Über dispatchTouchEvent aktiv auf dem ganzen Bildschirm (auch über Zeilen mit Long-Press).
        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (!monthView || e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > Math.abs(e2.getY() - e1.getY())
                        && Math.abs(dx) > dp(60) && Math.abs(vx) > dp(60)) {
                    monthOffset += dx > 0 ? -1 : 1;
                    reload();
                    return true;
                }
                return false;
            }
        });

        MaterialButtonToggleGroup toggleScope = findViewById(R.id.toggleScope);
        toggleScope.check(R.id.btnScopeMain);
        toggleScope.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            includeSubs = checkedId == R.id.btnScopeAll;
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    /** Wischgeste ganzflächig auswerten, danach normal weiterreichen (Scrollen/Klicks bleiben). */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeDetector != null) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---- Laden ----

    private void reload() {
        long start, end;
        Calendar c = Calendar.getInstance();
        if (monthView) {
            c.set(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.MONTH, monthOffset);   // per Wischgeste vor/zurück
            start = c.getTimeInMillis();
            displayYear = c.get(Calendar.YEAR);
            displayMonth = c.get(Calendar.MONTH) + 1;
            c.add(Calendar.MONTH, 1);
            end = c.getTimeInMillis();
        } else {
            c.clear();
            c.set(Calendar.YEAR, year);
            start = c.getTimeInMillis();
            displayYear = year;
            displayMonth = 0;
            c.add(Calendar.YEAR, 1);
            end = c.getTimeInMillis();
        }
        long now = System.currentTimeMillis();
        elapsed = now <= start ? 0 : (now >= end ? 1 : (double) (now - start) / (end - start));

        Calendar rc = Calendar.getInstance();
        rc.add(Calendar.YEAR, -2);
        final long recentStart = rc.getTimeInMillis();
        final long recentEnd = now;
        final long fStart = start, fEnd = end;
        final int budgetYear = displayYear;
        repository.getCategoryTypes(types -> {
            categoryTypes.clear();
            categoryTypes.putAll(types);
            repository.getCategoryActuals(fStart, fEnd, list -> {
                actuals.clear();
                actuals.addAll(list);
                repository.getCategoryActuals(recentStart, recentEnd, recent -> {
                    recentActuals.clear();
                    recentActuals.addAll(recent);
                    repository.getBudget(budgetYear, yb -> {
                        budgetSource = yb.source;
                        budgetLines.clear();
                        budgetLines.addAll(yb.lines);
                        if (budgetSource == null && settings.isBudgetInternal()) {
                            // Automatisch aus dem Verlauf berechnen und erneut laden.
                            repository.computeBudgetFromHistory(budgetYear, this::reload);
                            return;
                        }
                        render();
                    });
                });
            });
        });
    }

    // ---- Rendern ----

    private void render() {
        container.removeAllViews();

        if (budgetSource == null) {
            renderEmptyState();
            return;
        }

        if (monthView) {
            addMonthNavHeader();
        }
        Map<String, Cat> cats = buildCats();
        String currency = Currencies.getDefault();
        renderSection(cats, true, getString(R.string.budget_income_header), currency);
        renderSection(cats, false, getString(R.string.budget_expense_header), currency);
    }

    /**
     * Kopfzeile der Monatssicht: zentriert der angezeigte Monat, links (grau) der vorige, rechts (grau)
     * der nächste. Tippen auf links/rechts blättert – wie die Wischgeste – einen Monat zurück/vor.
     */
    private void addMonthNavHeader() {
        Calendar cur = Calendar.getInstance();
        cur.set(Calendar.DAY_OF_MONTH, 1);
        cur.add(Calendar.MONTH, monthOffset);
        Calendar prev = (Calendar) cur.clone();
        prev.add(Calendar.MONTH, -1);
        Calendar next = (Calendar) cur.clone();
        next.add(Calendar.MONTH, 1);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, dp(4), 0, dp(8));

        TextView left = monthLabel(prev, false);
        left.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        left.setOnClickListener(v -> {
            monthOffset--;
            reload();
        });
        TextView center = monthLabel(cur, true);
        center.setGravity(Gravity.CENTER);
        TextView right = monthLabel(next, false);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        right.setOnClickListener(v -> {
            monthOffset++;
            reload();
        });

        header.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(center, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(header);
    }

    /** Monatslabel; {@code center} = angezeigter Monat (fett, mit Jahr), sonst grauer Monatsname. */
    private TextView monthLabel(Calendar cal, boolean center) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        TextView tv = new TextView(this);
        tv.setText(new SimpleDateFormat(center ? "MMMM yyyy" : "MMMM", locale).format(cal.getTime()));
        if (center) {
            tv.setTextSize(18);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            tv.setTextColor(Color.GRAY);
        }
        return tv;
    }

    private void renderEmptyState() {
        TextView hint = new TextView(this);
        hint.setText(R.string.budget_empty);
        hint.setPadding(0, dp(16), 0, dp(16));
        container.addView(hint);

        MaterialButton compute = new MaterialButton(this);
        compute.setText(R.string.budget_compute);
        compute.setOnClickListener(v ->
                repository.computeBudgetFromHistory(displayYear, this::reload));
        container.addView(compute);

        if (settings.isKmyMode()) {
            MaterialButton imp = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            imp.setText(R.string.budget_import);
            imp.setOnClickListener(v ->
                    BudgetImportFlow.run(this, settings, repository, displayYear, this::reload));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            container.addView(imp, lp);
        }
    }

    /** Kategorie-Typ (global bestimmt) + aktueller Ist (vorzeichenbehaftet) + Soll. */
    private static final class Cat {
        boolean isIncome;
        long ist;          // aktueller Zeitraum, vorzeichenbehaftet Richtung Kategorietyp
        long displaySoll;  // Jahr bzw. /12
        long annualSoll;
    }

    /**
     * Baut je Kategorie den Typ, den aktuellen Ist und das Soll. Der Typ richtet sich <b>allein nach der
     * kmy-Datei</b> ({@link #categoryTypes}, ersatzweise dem – ebenfalls aus der Datei stammenden – Typ der
     * Budgetzeile), nicht nach dem Vorzeichen der Buchungen: eine Einnahme auf einer Ausgabekategorie
     * (z. B. Erstattung) mindert dort den Ist, kippt die Kategorie aber nicht. Kategorien ohne kmy-Typ
     * werden übersprungen. Es zählen nur Kategorien mit Zahlungen in den letzten 2 Jahren; ein negativer
     * Netto-Ist wird auf 0 gesetzt.
     *
     * <p>Soll: Jahressicht = Jahreswert (Summe der Monatszeilen). Monatssicht = der Wert des aktuellen
     * Monats bei monatsgenauen Budgets ({@code month=1..12}), sonst Jahreswert/12.</p>
     */
    private Map<String, Cat> buildCats() {
        Map<String, Long> period = sums(actuals);
        Map<String, Long> recent = sums(recentActuals);

        // Budgetzeilen je Kategorie einsammeln: Jahres-Soll (month=0) bzw. Monatswerte (month=1..12).
        Map<String, Long> annualByCat = new java.util.HashMap<>();
        Map<String, long[]> monthlyByCat = new java.util.HashMap<>();
        Map<String, Boolean> budgetTypeByCat = new java.util.HashMap<>();
        for (Budget b : budgetLines) {
            budgetTypeByCat.put(b.category, b.isIncome);
            if (b.month >= 1 && b.month <= 12) {
                long[] m = monthlyByCat.get(b.category);
                if (m == null) {
                    m = new long[12];
                    monthlyByCat.put(b.category, m);
                }
                m[b.month - 1] += b.amountCents;
            } else {
                annualByCat.put(b.category, b.amountCents);
            }
        }
        int curMonth = monthView ? displayMonth : 1;   // angezeigter Monat (folgt der Wischgeste)

        Map<String, Cat> cats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Long> e : recent.entrySet()) {
            String cat = e.getKey();   // in der 2-Jahres-Liste = Zahlung im Zeitraum vorhanden
            Boolean isInc = categoryTypes.get(cat);
            if (isInc == null) {
                isInc = budgetTypeByCat.get(cat);   // Rückfall auf den kmy-Typ der Budgetzeile
            }
            if (isInc == null) {
                continue;   // strikt: nur Kategorien mit kmy-Typ
            }
            long net = period.containsKey(cat) ? period.get(cat) : 0;
            Cat c = new Cat();
            c.isIncome = isInc;
            c.ist = de.spahr.ausgaben.db.BudgetMath.ist(isInc, net);   // Typ-Richtung, Clamp auf 0
            long[] monthly = monthlyByCat.get(cat);
            if (monthly != null) {
                long annual = 0;
                for (long v : monthly) {
                    annual += v;
                }
                c.annualSoll = annual;
                c.displaySoll = monthView ? monthly[curMonth - 1] : annual;   // Monat = konkreter Monatswert
            } else {
                c.annualSoll = annualByCat.containsKey(cat) ? annualByCat.get(cat) : 0;
                c.displaySoll = monthView ? Math.round(c.annualSoll / 12.0) : c.annualSoll;
            }
            cats.put(cat, c);
        }
        return cats;
    }

    /** Vorzeichenbehafteter Netto-Zufluss je Kategorie ({@code +} = rein, {@code −} = raus). */
    private Map<String, Long> sums(List<CategorySum> list) {
        Map<String, Long> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CategorySum s : list) {
            if (s.category == null || s.category.isEmpty()) {
                continue;
            }
            Long v = m.get(s.category);
            m.put(s.category, (v == null ? 0L : v) + s.total);
        }
        return m;
    }

    /** Sichtbar, wenn ein aktueller Ist vorliegt oder das Jahres-Soll ≥ 10 € ist. */
    private boolean visible(long ist, long annualSoll) {
        return ist != 0 || annualSoll >= 1000;
    }

    private static String mainOf(String cat) {
        int i = cat.indexOf(':');
        return i < 0 ? cat : cat.substring(0, i);
    }

    /** Ein Abschnitt (Einnahmen bzw. Ausgaben) mit Überschrift und aggregierten Kategoriezeilen. */
    private void renderSection(Map<String, Cat> cats, boolean income, String header, String currency) {
        // Haupt → [ist, displaySoll, annualSoll]; Haupt → (Unter-Vollpfad → [ist, displaySoll, annualSoll]).
        Map<String, long[]> mainTot = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Map<String, long[]>> subTot = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, Cat> e : cats.entrySet()) {
            Cat c = e.getValue();
            if (c.isIncome != income) {
                continue;
            }
            String cat = e.getKey();
            String main = mainOf(cat);
            long[] mv = mainTot.get(main);
            if (mv == null) {
                mv = new long[3];
                mainTot.put(main, mv);
            }
            mv[0] += c.ist;
            mv[1] += c.displaySoll;
            mv[2] += c.annualSoll;
            if (cat.contains(":")) {
                Map<String, long[]> subs = subTot.get(main);
                if (subs == null) {
                    subs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    subTot.put(main, subs);
                }
                subs.put(cat, new long[]{c.ist, c.displaySoll, c.annualSoll});
            }
        }

        boolean headerAdded = false;
        for (Map.Entry<String, long[]> e : mainTot.entrySet()) {
            String main = e.getKey();
            long[] mv = e.getValue();

            List<Map.Entry<String, long[]>> visibleSubs = new ArrayList<>();
            if (includeSubs) {
                Map<String, long[]> subs = subTot.get(main);
                if (subs != null) {
                    for (Map.Entry<String, long[]> se : subs.entrySet()) {
                        if (visible(se.getValue()[0], se.getValue()[2])) {
                            visibleSubs.add(se);
                        }
                    }
                }
            }

            if (!visible(mv[0], mv[2]) && visibleSubs.isEmpty()) {
                continue;
            }

            if (!headerAdded) {
                addHeader(header);
                headerAdded = true;
            }
            addRow(main, main, income, mv[0], mv[1], true, currency);
            for (Map.Entry<String, long[]> se : visibleSubs) {
                String label = se.getKey().substring(se.getKey().indexOf(':') + 1);
                addRow(label, se.getKey(), income, se.getValue()[0], se.getValue()[1], false, currency);
            }
        }
    }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(16), 0, dp(4));
        container.addView(tv);
    }

    private void addRow(String label, String category, boolean income, long ist, long soll,
                        boolean main, String currency) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(main ? 0 : dp(16), dp(6), 0, dp(2));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(this);
        name.setText(label);
        if (main) {
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        }
        LinearLayout.LayoutParams namLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        line.addView(name, namLp);

        TextView value = new TextView(this);
        value.setText(getString(R.string.budget_ist_soll,
                MoneyFormat.display(ist, currency), MoneyFormat.display(soll, currency)));
        value.setGravity(Gravity.END);
        line.addView(value);

        row.addView(line);
        row.addView(buildBar(income, ist, soll));

        boolean editable = !Budget.SOURCE_KMY.equals(budgetSource);
        if (editable) {
            row.setOnLongClickListener(v -> {
                showEditDialog(category, income, currency);
                return true;
            });
        }
        container.addView(row);
    }

    /** Dünner Balken: Breite = min(Ist/Soll,1); Farbe grün/rot je nach Zeitanteil (Einnahmen invers). */
    private View buildBar(boolean income, long ist, long soll) {
        double used = soll > 0 ? (double) ist / soll : (ist > 0 ? 1 : 0);
        double filled = Math.max(0, Math.min(used, 1));

        boolean good;
        if (income) {
            good = used >= elapsed;   // Einnahmen: schneller als die Zeit = gut
        } else {
            good = used <= elapsed;   // Ausgaben: langsamer als die Zeit = gut
        }
        int color = getColor(good ? R.color.income_green : R.color.expense_red);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        barLp.topMargin = dp(3);
        bar.setLayoutParams(barLp);

        View fill = new View(this);
        fill.setBackgroundColor(color);
        fill.setAlpha(0.4f);
        bar.addView(fill, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, (float) filled));

        if (filled < 1) {
            View rest = new View(this);
            bar.addView(rest, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, (float) (1 - filled)));
        }
        return bar;
    }

    private void showEditDialog(String category, boolean income, String currency) {
        long current = 0;
        for (Budget b : budgetLines) {
            if (b.isIncome == income && b.category.equals(category)) {
                current = b.amountCents;
                break;
            }
        }

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.budget_edit_hint));
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(til.getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (current > 0) {
            input.setText(MoneyFormat.plain(current));
        }
        til.addView(input);
        int pad = dp(16);
        til.setPadding(pad, 0, pad, 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.budget_edit_title, category))
                .setView(til)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Long cents = parseCents(input.getText() == null ? "" : input.getText().toString());
                    if (cents == null) {
                        Toast.makeText(this, R.string.budget_invalid_amount, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.saveBudgetLine(displayYear, category, income, cents, this::reload);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Long parseCents(String s) {
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
