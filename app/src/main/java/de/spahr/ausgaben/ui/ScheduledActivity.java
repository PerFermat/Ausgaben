package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.ScheduleProjection;
import de.spahr.ausgaben.db.ScheduledTransaction;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Seite „Geplante Buchungen": faltet jede aus KMyMoney importierte Planung in ihre einzelnen Termine auf
 * und zeigt sie als eine chronologische Liste (nach Fälligkeit). Vor jeder Zeile ein farbiger Strich –
 * grün = Einzahlung, rot = Auszahlung, gelb = Umbuchung. Termine ohne Datum oder älter als einen Monat
 * werden ausgelassen; nach vorn wird bis {@link #FORWARD_MONTHS} Monate projiziert.
 */
public class ScheduledActivity extends LocalizedActivity {

    /** Projektionshorizont nach vorn (Monate) und Kappung je Planung. */
    private static final int FORWARD_MONTHS = 24;   // max. 2 Jahre in die Zukunft
    private static final int MAX_PER_SCHEDULE = 120; // deckt 2 Jahre auch bei wöchentlichen ab

    private Repository repository;
    private LinearLayout container;

    /** Ein einzelner Fälligkeitstermin einer Planung. */
    private static final class Occurrence {
        final long dueMs;
        final ScheduledTransaction st;
        Occurrence(long dueMs, ScheduledTransaction st) {
            this.dueMs = dueMs;
            this.st = st;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        repository = new Repository(this);
        container = findViewById(R.id.scheduledContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        repository.getScheduledTransactions(this::render);
    }

    private void render(List<ScheduledTransaction> list) {
        container.removeAllViews();

        // Termine ab der gespeicherten nächsten Fälligkeit (die Projektion geht nur vorwärts) bis
        // FORWARD_MONTHS in der Zukunft; als Untergrenze werden Termine älter als ein Monat ausgelassen.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        long fromMs = cal.getTimeInMillis();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(Calendar.MONTH, FORWARD_MONTHS);
        long toMs = cal.getTimeInMillis();

        // Jede Planung in ihre Einzeltermine auffalten (ohne Datum → ignorieren).
        List<Occurrence> items = new ArrayList<>();
        for (ScheduledTransaction st : list) {
            if (st.nextDueMs <= 0) {
                continue;
            }
            for (long due : ScheduleProjection.occurrences(st.nextDueMs, st.occurrence,
                    st.occurrenceMultiplier, st.endMs, fromMs, toMs, MAX_PER_SCHEDULE)) {
                items.add(new Occurrence(due, st));
            }
        }
        if (items.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(R.string.scheduled_empty);
            hint.setPadding(0, dp(16), 0, dp(16));
            container.addView(hint);
            return;
        }
        Collections.sort(items, Comparator.comparingLong(o -> o.dueMs));

        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
        for (Occurrence o : items) {
            container.addView(buildRow(o, df));
        }
    }

    private View buildRow(Occurrence o, DateFormat df) {
        ScheduledTransaction st = o.st;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        // Farbstrich: grün = Einzahlung, rot = Auszahlung, gelb = Umbuchung.
        View strip = new View(this);
        int color;
        if (st.kind == ScheduledTransaction.KIND_INCOME) {
            color = R.color.income_green;
        } else if (st.kind == ScheduledTransaction.KIND_TRANSFER) {
            color = R.color.transfer_yellow;
        } else {
            color = R.color.expense_red;
        }
        strip.setBackgroundColor(getColor(color));
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(dp(4), dp(36));
        stripLp.setMarginEnd(dp(12));
        row.addView(strip, stripLp);

        // Eigene Datumsspalte vorne (dieser Termin).
        TextView date = new TextView(this);
        date.setText(df.format(new java.util.Date(o.dueMs)));
        date.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateLp.setMarginEnd(dp(12));
        date.setMinWidth(dp(72));
        row.addView(date, dateLp);

        // Mittelspalte: Name (fett) + Empfänger/Kategorie (grau).
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(primaryLabel(st));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        mid.addView(title);

        String detail = st.counterparty.isEmpty() ? st.payee : st.counterparty;
        if (!detail.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(detail);
            sub.setTextSize(13);
            sub.setTextColor(0xFF9E9E9E);
            mid.addView(sub);
        }

        row.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Betrag rechts.
        TextView amount = new TextView(this);
        amount.setText(MoneyFormat.display(st.amountCents, Currencies.forAccount(st.account)));
        amount.setGravity(Gravity.END);
        amount.setTypeface(amount.getTypeface(), Typeface.BOLD);
        row.addView(amount, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return row;
    }

    /** Anzeigename: Planungsname, sonst Empfänger, sonst Kategorie/Zielkonto. */
    private String primaryLabel(ScheduledTransaction st) {
        if (!st.name.isEmpty()) {
            return st.name;
        }
        if (!st.payee.isEmpty()) {
            return st.payee;
        }
        return st.counterparty;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
