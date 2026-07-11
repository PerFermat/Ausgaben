package de.spahr.ausgaben.ui;

import android.os.Bundle;
import android.view.Gravity;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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

    private final List<CategorySum> actuals = new ArrayList<>();
    /** Ist-Summen der letzten 2 Jahre – für Kategorie-Typ und Aktivitätsfilter. */
    private final List<CategorySum> recentActuals = new ArrayList<>();
    private String budgetSource;            // null = kein Budget vorhanden
    private final List<Budget> budgetLines = new ArrayList<>();
    private double elapsed = 0;

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
            reload();
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
            start = c.getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            end = c.getTimeInMillis();
        } else {
            c.clear();
            c.set(Calendar.YEAR, year);
            start = c.getTimeInMillis();
            c.add(Calendar.YEAR, 1);
            end = c.getTimeInMillis();
        }
        long now = System.currentTimeMillis();
        elapsed = now <= start ? 0 : (now >= end ? 1 : (double) (now - start) / (end - start));

        Calendar rc = Calendar.getInstance();
        rc.add(Calendar.YEAR, -2);
        final long recentStart = rc.getTimeInMillis();
        final long recentEnd = now;
        repository.getCategoryActuals(start, end, list -> {
            actuals.clear();
            actuals.addAll(list);
            repository.getCategoryActuals(recentStart, recentEnd, recent -> {
                recentActuals.clear();
                recentActuals.addAll(recent);
                repository.getBudget(year, yb -> {
                    budgetSource = yb.source;
                    budgetLines.clear();
                    budgetLines.addAll(yb.lines);
                    if (budgetSource == null && settings.isBudgetInternal()) {
                        // Automatisch aus dem Verlauf berechnen und erneut laden.
                        repository.computeBudgetFromHistory(year, this::reload);
                        return;
                    }
                    render();
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

        Map<String, Cat> cats = buildCats();
        String currency = Currencies.getDefault();
        renderSection(cats, true, getString(R.string.budget_income_header), currency);
        renderSection(cats, false, getString(R.string.budget_expense_header), currency);
    }

    private void renderEmptyState() {
        TextView hint = new TextView(this);
        hint.setText(R.string.budget_empty);
        hint.setPadding(0, dp(16), 0, dp(16));
        container.addView(hint);

        MaterialButton compute = new MaterialButton(this);
        compute.setText(R.string.budget_compute);
        compute.setOnClickListener(v ->
                repository.computeBudgetFromHistory(year, this::reload));
        container.addView(compute);

        if (settings.isKmyMode()) {
            MaterialButton imp = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            imp.setText(R.string.budget_import);
            imp.setOnClickListener(v ->
                    BudgetImportFlow.run(this, settings, repository, year, this::reload));
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
     * Baut je Kategorie den Typ, den aktuellen Ist und das Soll. Der Typ richtet sich nach der
     * <b>Kategorie</b> (Budget bzw. 2-Jahres-Netto), nicht nach der einzelnen Buchung – eine Einnahme auf
     * einer Ausgabekategorie (z. B. Erstattung) mindert dort den Ist. Es zählen nur Kategorien mit
     * Zahlungen in den letzten 2 Jahren.
     */
    private Map<String, Cat> buildCats() {
        Map<String, long[]> period = sums(actuals);
        Map<String, long[]> recent = sums(recentActuals);

        Map<String, Long> annualByCat = new java.util.HashMap<>();
        Map<String, Boolean> typeByCat = new java.util.HashMap<>();
        for (Budget b : budgetLines) {
            annualByCat.put(b.category, b.amountCents);
            typeByCat.put(b.category, b.isIncome);
        }

        Map<String, Cat> cats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, long[]> e : recent.entrySet()) {
            long[] r = e.getValue();
            if (r[0] == 0 && r[1] == 0) {
                continue;   // keine Zahlung in den letzten 2 Jahren
            }
            String cat = e.getKey();
            Boolean isInc = typeByCat.get(cat);
            if (isInc == null) {
                isInc = r[0] > r[1];   // ohne Budget: Typ aus dem 2-Jahres-Netto
            }
            long[] pp = period.get(cat);
            long pInc = pp == null ? 0 : pp[0];
            long pExp = pp == null ? 0 : pp[1];
            Cat c = new Cat();
            c.isIncome = isInc;
            c.ist = isInc ? pInc - pExp : pExp - pInc;
            c.annualSoll = annualByCat.containsKey(cat) ? annualByCat.get(cat) : 0;
            c.displaySoll = monthView ? Math.round(c.annualSoll / 12.0) : c.annualSoll;
            cats.put(cat, c);
        }
        return cats;
    }

    /** Einnahme-/Ausgabe-Summen je Kategorie (beide positiv): [0]=Einnahme, [1]=Ausgabe. */
    private Map<String, long[]> sums(List<CategorySum> list) {
        Map<String, long[]> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CategorySum s : list) {
            if (s.category == null || s.category.isEmpty()) {
                continue;
            }
            long[] a = m.get(s.category);
            if (a == null) {
                a = new long[2];
                m.put(s.category, a);
            }
            a[s.isIncome ? 0 : 1] += s.total;
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
            row.setOnClickListener(v -> showEditDialog(category, income, currency));
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
                    repository.saveBudgetLine(year, category, income, cents, this::reload);
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
