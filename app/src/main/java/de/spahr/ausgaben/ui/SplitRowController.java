package de.spahr.ausgaben.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;

/**
 * Verwaltet die dynamische Kategorie-/Teilbetrag-Liste (Splitbuchung) im Buchungseditor: fügt Zeilen an,
 * hält genau eine leere Abschlusszeile, koppelt Gesamtbetrag ↔ Teilbeträge und prüft die Gültigkeit
 * (Summe der Teile = Gesamt). Ausgelagert aus {@link BookingEditActivity}, um deren Umfang zu verringern.
 *
 * <p>Der Gesamtbetrag lebt weiterhin im {@code editAmount}-Feld der Activity (auch für Umbuchungen genutzt);
 * dieser Controller hält nur eine Referenz darauf. Bei jeder relevanten Änderung meldet er sich über
 * {@code onChanged} zurück (→ Buttons freischalten).</p>
 */
class SplitRowController {

    /** Einfacher Kategorie/Teilbetrag-Datensatz während der Editor-Eingabe. */
    static final class Part {
        final String category;
        final long cents;
        /** Typ der Kategorie (true=Einnahme, false=Ausgabe, null=unbekannt/frei getippt). */
        final Boolean categoryIsIncome;

        Part(String category, long cents) {
            this(category, cents, null);
        }

        Part(String category, long cents, Boolean categoryIsIncome) {
            this.category = category;
            this.cents = cents;
            this.categoryIsIncome = categoryIsIncome;
        }
    }

    private final LinearLayout container;
    private final TextInputEditText totalField;
    private final LayoutInflater inflater;
    private final boolean readOnly;
    private final Runnable onChanged;

    private CategoryFilterAdapter categoryAdapter;
    private boolean suppressSplitEvents;
    private boolean syncingAmounts;
    private AmountFieldBinder amountBinder;

    /** Hängt ein Teilbetrag-Feld an die gemeinsame Rechentastatur (von der Activity gesetzt). */
    interface AmountFieldBinder {
        void bind(TextInputEditText field);
    }

    SplitRowController(LinearLayout container, TextInputEditText totalField, LayoutInflater inflater,
                       boolean readOnly, Runnable onChanged) {
        this.container = container;
        this.totalField = totalField;
        this.inflater = inflater;
        this.readOnly = readOnly;
        this.onChanged = onChanged;
    }

    /** Setzt den Binder, mit dem jedes Teilbetrag-Feld an die Rechentastatur gebunden wird. */
    void setAmountBinder(AmountFieldBinder binder) {
        this.amountBinder = binder;
    }

    /** Setzt den (gruppierten) Kategorie-Adapter und wendet ihn auf bestehende Zeilen an. */
    void setAdapter(CategoryFilterAdapter adapter) {
        this.categoryAdapter = adapter;
        applyAdapterToRows();
    }

    void applyAdapterToRows() {
        for (int i = 0; i < container.getChildCount(); i++) {
            MaterialAutoCompleteTextView cat = container.getChildAt(i).findViewById(R.id.splitCategory);
            if (cat != null) {
                cat.setAdapter(categoryAdapter);
            }
        }
    }

    /** Entfernt alle Zeilen (vor dem Vorbelegen). */
    void clear() {
        container.removeAllViews();
    }

    /** Beim Bulk-Vorbelegen die Zeilen-Events unterdrücken. */
    void setSuppressEvents(boolean suppress) {
        this.suppressSplitEvents = suppress;
    }

    void addRow(String category, String amountText) {
        addRow(category, amountText, null);
    }

    /**
     * Wie {@link #addRow(String, String)}, übernimmt zusätzlich einen bereits bekannten Kategorietyp
     * (z. B. beim Vorbelegen einer bestehenden Buchung) als gemerkten Zustand der Zeile, ohne dass der
     * Nutzer die Kategorie erneut in der Auswahlliste antippen müsste.
     */
    void addRow(String category, String amountText, Boolean categoryIsIncome) {
        View row = inflater.inflate(R.layout.item_split_row, container, false);
        MaterialAutoCompleteTextView cat = row.findViewById(R.id.splitCategory);
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        View remove = row.findViewById(R.id.btnRemoveSplit);
        if (categoryAdapter != null) {
            cat.setAdapter(categoryAdapter);
        }
        // Vorbelegung vor dem Anhängen der Listener, damit sie keine dynamische Logik auslösen.
        if (category != null) {
            cat.setText(category, false);
        }
        cat.setTag(categoryIsIncome);
        if (amountText != null) {
            amt.setText(amountText);
        }
        if (readOnly) {
            // Ansicht: Kategorie/Betrag gesperrt, kein Entfernen-Knopf.
            lockField(cat);
            lockField(amt);
            remove.setVisibility(View.GONE);
            container.addView(row);
            return;
        }
        cat.addTextChangedListener(new SimpleWatcher(() -> {
            // Frei getippt/geändert → gemerkten Typ verwerfen (kein Auswahl-Signal mehr gültig). Bei
            // einer Dropdown-Auswahl läuft dieser Watcher VOR dem Klick-Listener unten (Android ruft bei
            // performCompletion() erst setText(), dann den Item-Klick), der den Typ danach korrekt setzt.
            cat.setTag(null);
            onSplitCategoryChanged(row);
        }));
        cat.setOnItemClickListener((parent, view, position, id) -> {
            CategoryFilterAdapter.CatItem item =
                    categoryAdapter != null ? categoryAdapter.getItem(position) : null;
            cat.setTag(item != null ? item.groupIsIncome : null);
        });
        amt.addTextChangedListener(new SimpleWatcher(() -> onPartialChanged(row)));
        if (amountBinder != null) {
            amountBinder.bind(amt);   // Teilbetrag-Feld an die Rechentastatur binden
        }
        remove.setOnClickListener(v -> {
            container.removeView(row);
            ensureTrailingRow();
            recomputeTotalFromParts();
            onChanged.run();
        });
        container.addView(row);
    }

    private void onSplitCategoryChanged(View row) {
        if (suppressSplitEvents) {
            return;
        }
        int idx = container.indexOfChild(row);
        String cat = catText(row);
        // Erste Kategorie → Teilbetrag automatisch mit dem (vorhandenen) Gesamtbetrag vorbelegen.
        if (idx == 0 && !cat.isEmpty() && amtText(row).isEmpty() && currentTotalCents() > 0) {
            setAmtText(row, formatCents(currentTotalCents()));
        }
        // Kategorie in der letzten Zeile → neue leere Zeile anhängen.
        if (!cat.isEmpty() && idx == container.getChildCount() - 1) {
            addRow(null, null);
        }
        recomputeTotalFromParts();
        onChanged.run();
    }

    /** Teilbetrag geändert → Gesamtbetrag = Summe der Teilbeträge. */
    private void onPartialChanged(View row) {
        if (suppressSplitEvents || syncingAmounts) {
            onChanged.run();
            return;
        }
        recomputeTotalFromParts();
        onChanged.run();
    }

    /** Gesamtbetrag geändert → bei genau einer Kategorie deren Teilbetrag gleich dem Gesamtbetrag setzen. */
    void onTotalChanged() {
        if (suppressSplitEvents || syncingAmounts) {
            onChanged.run();
            return;
        }
        View single = singleCategoryRow();
        if (single != null) {
            syncingAmounts = true;
            setAmtText(single, formatCents(currentTotalCents()));
            syncingAmounts = false;
        }
        onChanged.run();
    }

    /** Setzt den Gesamtbetrag auf die Summe aller Teilbeträge (Zeilen mit Kategorie). */
    private void recomputeTotalFromParts() {
        long sum = 0;
        boolean any = false;
        for (int i = 0; i < container.getChildCount(); i++) {
            View r = container.getChildAt(i);
            if (catText(r).isEmpty()) {
                continue;
            }
            Long c = parseCents(amtText(r));
            if (c != null) {
                sum += c;
                any = true;
            }
        }
        if (!any) {
            return; // keine Kategorie mit Betrag → Gesamtbetrag unverändert lassen
        }
        syncingAmounts = true;
        totalField.setText(formatCents(sum));
        syncingAmounts = false;
    }

    /** Liefert die einzige Zeile mit gesetzter Kategorie oder {@code null}, wenn es 0 oder mehrere sind. */
    private View singleCategoryRow() {
        View found = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            View r = container.getChildAt(i);
            if (!catText(r).isEmpty()) {
                if (found != null) {
                    return null;
                }
                found = r;
            }
        }
        return found;
    }

    /** Sorgt für genau eine leere Abschluss-Zeile am Ende. */
    void ensureTrailingRow() {
        if (readOnly) {
            return; // Ansicht: keine leere Zusatzzeile.
        }
        int n = container.getChildCount();
        if (n == 0) {
            addRow(null, null);
            return;
        }
        if (!catText(container.getChildAt(n - 1)).isEmpty()) {
            addRow(null, null);
        }
    }

    /** Kategorie-Teile mit gültiger Kategorie und Betrag (leere Abschlusszeile wird ignoriert). */
    List<Part> collectParts() {
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View r = container.getChildAt(i);
            String c = catText(r);
            if (c.isEmpty()) {
                continue;
            }
            Long cents = parseCents(amtText(r));
            if (cents != null) {
                parts.add(new Part(c, cents, categoryIsIncomeTag(r)));
            }
        }
        return parts;
    }

    /** Gemerkter Kategorietyp der Zeile (aus der Auswahlliste angetippt oder vorbelegt), sonst {@code null}. */
    private Boolean categoryIsIncomeTag(View row) {
        MaterialAutoCompleteTextView cat = row.findViewById(R.id.splitCategory);
        Object tag = cat.getTag();
        return tag instanceof Boolean ? (Boolean) tag : null;
    }

    /** true, wenn das Ausgabe/Einnahme-Formular gespeichert werden darf (Summe der Teile = Gesamt). */
    boolean isValid() {
        Long total = parseCents(text(totalField));
        if (total == null || total <= 0) {
            return false;
        }
        long sum = 0;
        int count = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View r = container.getChildAt(i);
            String c = catText(r);
            String a = amtText(r);
            if (c.isEmpty()) {
                continue; // leere / betragslose Kategoriezeile ignorieren
            }
            Long cents = parseCents(a);
            if (cents == null) {
                return false; // Kategorie ohne gültigen Teilbetrag
            }
            sum += cents;
            count++;
        }
        if (count == 0) {
            return true; // keine Kategorie → einfache (nicht zugeordnete) Buchung
        }
        return sum == total;
    }

    private String catText(View row) {
        MaterialAutoCompleteTextView cat = row.findViewById(R.id.splitCategory);
        return cat.getText() == null ? "" : cat.getText().toString().trim();
    }

    private String amtText(View row) {
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        return amt.getText() == null ? "" : amt.getText().toString().trim();
    }

    private void setAmtText(View row, String textValue) {
        TextInputEditText amt = row.findViewById(R.id.splitAmount);
        amt.setText(textValue);
    }

    private long currentTotalCents() {
        Long t = parseCents(text(totalField));
        return t == null ? 0 : t;
    }

    private void lockField(android.widget.EditText e) {
        e.setFocusable(false);
        e.setFocusableInTouchMode(false);
        e.setClickable(false);
        e.setLongClickable(false);
        e.setCursorVisible(false);
        e.setKeyListener(null);
        e.setOnClickListener(null);
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    private static String formatCents(long cents) {
        return de.spahr.ausgaben.settings.MoneyFormat.plain(cents);
    }

    /** Teilbetrag in Cent; akzeptiert wie das Gesamtfeld auch eine kleine Rechnung (z. B. {@code 12,50+3,20}). */
    private static Long parseCents(String raw) {
        BigDecimal value = de.spahr.ausgaben.settings.AmountExpression.evaluate(raw);
        if (value == null) {
            return null;
        }
        try {
            return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
