package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.SecurityTx;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Die Durchsicht nach dem Einlesen mehrerer Abrechnungen: was die App aus jeder Datei herausgelesen hat,
 * bevor irgendetwas gebucht wird.
 *
 * <p>Der Grund für den Zwischenschritt: bei einem Stapel sieht man einer einzelnen Maske nicht mehr an,
 * ob die übrigen Dateien auch getroffen haben. Hier steht alles nebeneinander — und was zum Buchen noch
 * nicht reicht, ist rot hinterlegt statt still übergangen. Gespeichert wird erst am Ende, und dann alles
 * zusammen: ein halb gebuchter Stapel wäre schwerer zu berichtigen als ein gar nicht gebuchter.</p>
 *
 * <p>Der Stift führt in dieselbe Erfassungsmaske wie sonst, nur dass sie dort nicht speichert, sondern
 * das Berichtigte zurückgibt ({@code SecurityTxEditActivity.EXTRA_BATCH}).</p>
 */
public class StatementBatchActivity extends LocalizedActivity {

    /** Die eingelesenen Entwürfe (siehe {@link StatementImport#openAll}). */
    public static final String EXTRA_DRAFTS = "drafts";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    private Repository repository;
    private LinearLayout container;
    private MaterialButton btnSave;

    private final ArrayList<StatementDraft> drafts = new ArrayList<>();
    /** Der Eintrag, der gerade in der Maske liegt; die Antwort kommt ohne eigene Kennung zurück. */
    private int editing = -1;
    private ActivityResultLauncher<Intent> editLauncher;
    /** Läuft das Speichern schon? Ein zweiter Tipp legte den Stapel sonst ein zweites Mal an. */
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_batch);
        repository = new Repository(this);

        ArrayList<StatementDraft> given = savedInstanceState != null
                ? savedInstanceState.getParcelableArrayList(EXTRA_DRAFTS)
                : getIntent().getParcelableArrayListExtra(EXTRA_DRAFTS);
        if (given != null) {
            drafts.addAll(given);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.statement_batch_title);
        toolbar.setNavigationOnClickListener(v -> confirmDiscard());
        container = findViewById(R.id.batchContainer);
        btnSave = findViewById(R.id.btnBatchSave);
        btnSave.setOnClickListener(v -> saveAll());
        findViewById(R.id.btnBatchCancel).setOnClickListener(v -> confirmDiscard());

        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        applyCorrection(result.getData());
                    }
                    editing = -1;
                });

        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        confirmDiscard();
                    }
                });

        if (drafts.isEmpty()) {
            Toast.makeText(this, R.string.statement_batch_none, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelableArrayList(EXTRA_DRAFTS, drafts);
    }

    // ---- Anzeige ----

    private void render() {
        // Vor jeder Anzeige neu: Bearbeiten und Löschen können eine Doppelung erst schaffen oder wieder
        // auflösen. Ob eine Bewegung schon im Depot steht, wird hier nicht neu abgefragt — das bringt
        // die Maske über EXTRA_DUPLICATE mit zurück.
        StatementDraft.markSelectionDuplicates(drafts);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < drafts.size(); i++) {
            container.addView(row(inflater, drafts.get(i), i));
        }
        toolbarSubtitle();
    }

    /**
     * Der Untertitel nennt den Grund, warum „Alle speichern" nicht geht. Ohne ihn suchte man in langer
     * Liste nach einer Taste, die sich ohne Angabe von Gründen nicht drücken lässt.
     */
    private void toolbarSubtitle() {
        boolean allBookable = true;
        for (StatementDraft d : drafts) {
            allBookable &= d.isBookable();
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(allBookable ? null : getString(R.string.statement_batch_incomplete));
        btnSave.setEnabled(allBookable);
    }

    private View row(LayoutInflater inflater, StatementDraft d, int index) {
        View view = inflater.inflate(R.layout.item_statement_draft, container, false);
        TextView security = view.findViewById(R.id.textDraftSecurity);
        TextView amount = view.findViewById(R.id.textDraftAmount);
        TextView details = view.findViewById(R.id.textDraftDetails);
        TextView problem = view.findViewById(R.id.textDraftProblem);

        // Erste Zeile: woran man den Eintrag erkennt. Ohne Wertpapier steht dort der Dateiname – das
        // Einzige, was von einer nicht zugeordneten Abrechnung übrig bleibt.
        security.setText(d.securityName.isEmpty() ? d.fileName : d.securityName);
        amount.setText(d.netCents == null ? "" : MoneyFormat.display(d.netCents, Currencies.getDefault()));
        details.setText(detailsOf(d));
        details.setVisibility(detailsOf(d).isEmpty() ? View.GONE : View.VISIBLE);

        // Rot geht vor Gelb: was gar nicht buchbar ist, muss zuerst berichtigt werden. Der Hinweis auf
        // eine Doppelung nimmt dieselbe Zeile, nur in gedecktem Gelb — er hält nichts auf.
        int reason = d.problem();
        int hint = reason == 0 ? d.duplicateHint() : 0;
        if (reason == 0 && hint == 0) {
            view.setBackgroundColor(0);
            problem.setVisibility(View.GONE);
        } else {
            view.setBackgroundColor(getColor(
                    reason == 0 ? R.color.statement_warn_bg : R.color.statement_error_bg));
            problem.setVisibility(View.VISIBLE);
            problem.setText(reason == R.string.statement_problem_security
                    ? getString(reason, d.isin) : getString(reason == 0 ? hint : reason));
        }

        ImageButton edit = view.findViewById(R.id.btnDraftEdit);
        edit.setOnClickListener(v -> edit(index));
        // Eine Datei, die sich nicht lesen ließ, gibt es nichts zu berichtigen – nur zu entfernen.
        edit.setVisibility(d.failure == 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnDraftDelete).setOnClickListener(v -> remove(index));
        return view;
    }

    /**
     * Die zweite Zeile: alles Übrige in normaler Schrift. Genannt wird nur, was auch dasteht — eine
     * Beschriftung ohne Wert wäre nur Platzverbrauch. Wird es lang, bricht die Zeile um.
     */
    private String detailsOf(StatementDraft d) {
        StringBuilder b = new StringBuilder();
        if (d.action != null) {
            append(b, actionLabel(d.action));
        }
        if (d.dateMillis > 0) {
            append(b, dateFormat.format(new Date(d.dateMillis)));
        }
        if (!d.isDividend()) {
            if (d.shares != null) {
                append(b, getString(R.string.security_tx_shares) + " " + MoneyFormat.shares(d.shares));
            }
            if (d.price != null) {
                append(b, getString(R.string.security_tx_price) + " "
                        + MoneyFormat.decimal(d.price, 2, 4));
            }
        } else if (d.grossCents != null) {
            append(b, getString(R.string.security_tx_gross) + " " + MoneyFormat.plain(d.grossCents));
        }
        if (d.feeCents != null && d.feeCents != 0) {
            append(b, getString(d.isDividend() ? R.string.security_tx_tax : R.string.security_tx_fee)
                    + " " + MoneyFormat.plain(d.feeCents));
        }
        if (!d.moneyAccount.trim().isEmpty()) {
            append(b, d.moneyAccount.trim());
        }
        String category = d.isDividend() ? d.incomeCategory : d.feeCategory;
        if (!category.trim().isEmpty()) {
            append(b, category.trim());
        }
        return b.toString();
    }

    private static void append(StringBuilder b, String part) {
        if (b.length() > 0) {
            b.append("  ·  ");
        }
        b.append(part);
    }

    private String actionLabel(String action) {
        switch (action) {
            case StatementDraft.SELL:
                return getString(R.string.action_sell);
            case StatementDraft.DIVIDEND:
                return getString(R.string.action_dividend);
            default:
                return getString(R.string.action_buy);
        }
    }

    // ---- Bearbeiten ----

    /**
     * Der Stift. Fehlt dem Eintrag noch das Wertpapier, ist das die erste Frage — die Maske gehört zu
     * einem Wertpapier und könnte es selbst nicht mehr wechseln.
     */
    private void edit(int index) {
        StatementDraft d = drafts.get(index);
        if (d.kmyId.isEmpty()) {
            // Auch ohne ISIN lässt sich wählen – nur gemerkt werden kann die Wahl dann nicht, es fehlt
            // der Schlüssel dafür. Im Titel steht dann der Dateiname.
            String label = d.isin == null ? d.fileName : d.isin;
            StatementImport.pickSecurity(this, repository, label, d.isin, s -> {
                d.depot = s.depot;
                d.kmyId = s.kmyId;
                d.securityName = s.name;
                loadDefaultsThenEdit(index);
            });
            return;
        }
        openEditor(index);
    }

    /** Frisch zugeordnet: erst Gegenkonto und Kategorien nachschlagen, dann in die Maske. */
    private void loadDefaultsThenEdit(int index) {
        StatementDraft d = drafts.get(index);
        if (d.action == null) {
            openEditor(index);
            return;
        }
        repository.getSecurityTxDefaults(d.depot, d.kmyId, d.action, last -> {
            if (last != null) {
                d.moneyAccount = last.moneyAccount;
                d.feeCategory = last.feeCategory;
                d.incomeCategory = last.incomeCategory;
            }
            openEditor(index);
        });
    }

    private void openEditor(int index) {
        StatementDraft d = drafts.get(index);
        editing = index;
        Intent i = new Intent(this, SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_BATCH, true);
        // Damit die Maske dasselbe sagt wie die Liste – die Doppelung in der Auswahl kennt nur sie.
        i.putExtra(SecurityTxEditActivity.EXTRA_DUPLICATE, d.duplicateHint());
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, d.depot);
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, d.kmyId);
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, d.securityName);
        if (d.action != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACTION, d.action);
        }
        if (d.dateMillis > 0) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_DATE, d.dateMillis);
        }
        if (d.shares != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_SHARES, (double) d.shares);
        }
        if (d.price != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_PRICE, (double) d.price);
        }
        if (d.feeCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_FEE, (long) d.feeCents);
        }
        if (d.netCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_NET, (long) d.netCents);
        }
        if (d.isDividend() && d.grossCents != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_GROSS, (long) d.grossCents);
        }
        i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACCOUNT, d.moneyAccount);
        i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_FEE_CATEGORY, d.feeCategory);
        i.putExtra(SecurityTxEditActivity.EXTRA_PREFILL_INCOME_CATEGORY, d.incomeCategory);
        if (d.stagedPath != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_FILE, d.stagedPath);
        }
        if (d.textPath != null) {
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_TEXT, d.textPath);
            i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_ISIN, d.isin);
        }
        editLauncher.launch(i);
    }

    /**
     * Was aus der Maske zurückkommt. Der Eintrag wird danach erneut geprüft — berichtigt er den Mangel,
     * verliert die Zeile ihr Rot von selbst.
     */
    private void applyCorrection(Intent data) {
        if (editing < 0 || editing >= drafts.size()) {
            return;
        }
        StatementDraft d = drafts.get(editing);
        d.action = data.getStringExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACTION);
        d.dateMillis = data.getLongExtra(SecurityTxEditActivity.EXTRA_PREFILL_DATE, -1);
        d.shares = optDouble(data, SecurityTxEditActivity.EXTRA_PREFILL_SHARES);
        d.price = optDouble(data, SecurityTxEditActivity.EXTRA_PREFILL_PRICE);
        d.grossCents = optLong(data, SecurityTxEditActivity.EXTRA_PREFILL_GROSS);
        d.feeCents = optLong(data, SecurityTxEditActivity.EXTRA_PREFILL_FEE);
        d.netCents = optLong(data, SecurityTxEditActivity.EXTRA_PREFILL_NET);
        d.moneyAccount = orEmpty(data.getStringExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACCOUNT));
        d.feeCategory = orEmpty(data.getStringExtra(SecurityTxEditActivity.EXTRA_PREFILL_FEE_CATEGORY));
        d.incomeCategory = orEmpty(
                data.getStringExtra(SecurityTxEditActivity.EXTRA_PREFILL_INCOME_CATEGORY));
        d.conflict = data.getBooleanExtra(SecurityTxEditActivity.EXTRA_CONFLICT, false);
        d.dupBooked = data.getBooleanExtra(SecurityTxEditActivity.EXTRA_DUP_BOOKED, d.dupBooked);
        // Die Maske hat schon gerechnet; hier wird nur ergänzt, was sie offen gelassen hat.
        d.resolve();
        render();
    }

    private static Double optDouble(Intent data, String key) {
        return data.hasExtra(key) ? data.getDoubleExtra(key, 0) : null;
    }

    private static Long optLong(Intent data, String key) {
        return data.hasExtra(key) ? data.getLongExtra(key, 0) : null;
    }

    // ---- Entfernen und Speichern ----

    /** Der Papierkorb entfernt nur den Eintrag — gebucht war noch nichts, zu fragen gibt es nichts. */
    private void remove(int index) {
        StatementDraft d = drafts.remove(index);
        discardStaged(d);
        if (drafts.isEmpty()) {
            finish();
            return;
        }
        render();
    }

    private void saveAll() {
        if (saving) {
            return;
        }
        List<SecurityTx> txs = new ArrayList<>();
        List<Booking> bookings = new ArrayList<>();
        for (StatementDraft d : drafts) {
            if (!d.isBookable()) {
                Toast.makeText(this, R.string.statement_batch_incomplete, Toast.LENGTH_LONG).show();
                return;
            }
            Booking booking = d.toBooking();
            // Die Abrechnung wird zum Beleg der Gegenbuchung – über denselben Weg wie die Belege der
            // übrigen Buchungen (Ablage, Jahresordner, Abgleich, Export).
            if (d.stagedPath != null) {
                booking.note = de.spahr.ausgaben.receipt.SingleReceipt.attach(this,
                        new java.io.File(d.stagedPath), booking.note, booking.createdAt);
                d.stagedPath = null;
            }
            txs.add(d.toTx());
            bookings.add(booking);
        }
        saving = true;
        btnSave.setEnabled(false);
        final int count = txs.size();
        repository.saveManualSecurityTxBatch(txs, bookings, () -> {
            Toast.makeText(this, getString(R.string.statement_batch_saved, count),
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void confirmDiscard() {
        AppDialog.destructive(this)
                .setTitle(R.string.statement_batch_discard_title)
                .setMessage(R.string.statement_batch_discard_message)
                .setPositiveButton(R.string.statement_batch_discard, (d, w) -> {
                    for (StatementDraft draft : drafts) {
                        discardStaged(draft);
                    }
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Die vorläufig abgelegte Kopie wieder wegräumen; sie hängt an keiner Buchung. */
    private static void discardStaged(StatementDraft d) {
        if (d.stagedPath != null) {
            //noinspection ResultOfMethodCallIgnored
            new java.io.File(d.stagedPath).delete();
            d.stagedPath = null;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
