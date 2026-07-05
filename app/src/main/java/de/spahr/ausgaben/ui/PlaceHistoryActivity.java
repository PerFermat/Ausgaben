package de.spahr.ausgaben.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.PlaceEntry;
import de.spahr.ausgaben.db.Repository;

/** Zeigt den Verlauf (Bewegungen) eines Ortes mit laufendem Saldo. */
public class PlaceHistoryActivity extends LocalizedActivity {

    public static final String EXTRA_PLACE = "place";
    public static final String EXTRA_ACCOUNT = "account";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String place = getIntent().getStringExtra(EXTRA_PLACE);
        String account = getIntent().getStringExtra(EXTRA_ACCOUNT);
        currency = de.spahr.ausgaben.settings.Currencies.forAccount(account);
        toolbar.setTitle(place == null ? "" : place);
        if (account != null && !account.isEmpty()) {
            toolbar.setSubtitle(account);
        }

        LinearLayout container = findViewById(R.id.historyContainer);
        Repository repository = new Repository(this);
        repository.getPlaceHistory(account, place, entries -> render(container, entries));
    }

    private void render(LinearLayout container, List<PlaceEntry> entries) {
        container.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_movements);
            container.addView(empty);
            return;
        }
        long running = 0;
        for (PlaceEntry e : entries) {
            running += e.amountCents;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);

            TextView left = new TextView(this);
            left.setText(dateFormat.format(new Date(e.createdAt)) + "  ·  " + typeLabel(e.type));
            left.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView amount = new TextView(this);
            amount.setText(formatEuro(e.amountCents));
            amount.setTextColor(e.amountCents < 0 ? getColor(R.color.expense_red) : getColor(R.color.income_green));

            TextView bal = new TextView(this);
            bal.setText("  = " + formatEuro(running));
            bal.setGravity(Gravity.END);
            bal.setTextColor(getColor(R.color.grey_text));
            bal.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));

            row.addView(left);
            row.addView(amount);
            row.addView(bal);
            container.addView(row);
        }
    }

    private String typeLabel(String type) {
        switch (type == null ? "" : type) {
            case "transfer":
                return getString(R.string.movement_transfer);
            case "reconcile":
                return getString(R.string.movement_reconcile);
            default:
                return getString(R.string.movement_booking);
        }
    }

    /** Währungskennzeichen des Kontos dieses Ort-Verlaufs. */
    private String currency = de.spahr.ausgaben.settings.Currencies.getDefault();

    private String formatEuro(long signedCents) {
        long euros = signedCents / 100;
        long cents = Math.abs(signedCents % 100);
        String sign = (signedCents < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents) + " " + currency;
    }
}
