package de.spahr.ausgaben.ui;

import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.db.SecurityTx;
import de.spahr.ausgaben.settings.AmountExpression;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.SettingsStore;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.StatementTemplate;
import de.spahr.ausgaben.statement.TemplateCheck;
import de.spahr.ausgaben.statement.TemplateLearner;
import de.spahr.ausgaben.util.SecurityAmounts;
import de.spahr.ausgaben.util.SecurityAmounts.Field;

/**
 * Erfassen und Ansehen einer Depot-Bewegung eines <b>bestehenden</b> Wertpapiers. Anlegbar sind nur Kauf,
 * Verkauf und Dividende; Ein-/Ausbuchungen und Wiederanlagen aus KMyMoney lassen sich hier nur ansehen.
 *
 * <p>Drei Modi: <b>Neu</b> (das Plus in der Bewegungsliste), <b>Ansehen</b> (Tipp auf eine importierte
 * Bewegung – dieser Modus hat die frühere Info-Popup abgelöst) und <b>Ändern</b> (Tipp auf eine selbst
 * erfasste, noch nicht exportierte Bewegung).</p>
 *
 * <p>Von den vier Zahlenfeldern ergänzt {@link SecurityAmounts} jeweils das fehlende. Die Maske merkt sich
 * dafür, welche Felder der Nutzer selbst gefüllt hat – nur die übrigen werden überschrieben.</p>
 */
public class SecurityTxEditActivity extends LocalizedActivity {

    public static final String EXTRA_DEPOT = "depot";
    public static final String EXTRA_KMY_ID = "kmyId";
    public static final String EXTRA_NAME = "name";
    /** Id der anzusehenden/zu ändernden Bewegung; fehlt sie, wird eine neue angelegt. */
    public static final String EXTRA_TX_ID = "txId";

    // ---- Vorbelegung aus einer eingelesenen Bankabrechnung (siehe StatementImport) ----
    public static final String EXTRA_PREFILL_ACTION = "prefillAction";
    public static final String EXTRA_PREFILL_DATE = "prefillDate";
    public static final String EXTRA_PREFILL_SHARES = "prefillShares";
    public static final String EXTRA_PREFILL_PRICE = "prefillPrice";
    public static final String EXTRA_PREFILL_FEE = "prefillFee";
    public static final String EXTRA_PREFILL_NET = "prefillNet";
    public static final String EXTRA_PREFILL_GROSS = "prefillGross";
    public static final String EXTRA_PREFILL_ACCOUNT = "prefillAccount";
    public static final String EXTRA_PREFILL_FEE_CATEGORY = "prefillFeeCategory";
    public static final String EXTRA_PREFILL_INCOME_CATEGORY = "prefillIncomeCategory";
    /**
     * Die Maske gehört zu einem Eintrag der Erkennungsliste ({@link StatementBatchActivity}): dort wird
     * nicht gespeichert, sondern berichtigt. Gebucht wird der ganze Stapel erst am Ende.
     */
    public static final String EXTRA_BATCH = "batch";
    /** Zurück an die Liste: Brutto, Steuer und Netto gehen nicht auf. */
    public static final String EXTRA_CONFLICT = "conflict";
    /**
     * Der Doppelungs-Hinweis, den die Erkennungsliste schon kennt (Textbaustein, 0 = keiner). Die
     * Doppelung <b>innerhalb der Auswahl</b> sieht nur die Liste – die Maske kennt immer nur einen Beleg.
     */
    public static final String EXTRA_DUPLICATE = "duplicate";
    /** Zurück an die Liste: diese Bewegung steht schon im Depot. */
    public static final String EXTRA_DUP_BOOKED = "dupBooked";
    /** Pfad zum zwischengespeicherten Abrechnungstext; daraus lernt die App beim Speichern die Anker. */
    public static final String EXTRA_STATEMENT_TEXT = "statementText";
    public static final String EXTRA_STATEMENT_ISIN = "statementIsin";
    /** Pfad der schon in die Belegablage kopierten Abrechnung; wird beim Speichern zum Beleg. */
    public static final String EXTRA_STATEMENT_FILE = "statementFile";

    private static final String BUY = "buy";
    private static final String SELL = "sell";
    private static final String DIVIDEND = "dividend";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final Calendar selectedDate = Calendar.getInstance();

    private Repository repository;
    private String depot;
    private String kmyId;
    private String securityName = "";
    private double taxRate;

    /** Die geladene Bewegung; {@code null} im Neu-Modus. */
    private SecurityTx loaded;
    private boolean readOnly;
    /** Berichtigen für die Erkennungsliste statt Speichern (siehe {@link #EXTRA_BATCH}). */
    private boolean batchMode;
    /** Die Maske kam aus einer eingelesenen Abrechnung — dann gilt: nicht gefunden heißt leer. */
    private boolean fromStatement;
    /**
     * Steht das Datum fest? {@code selectedDate} allein sagt das nicht: es trägt immer einen Wert, damit
     * der Kalender irgendwo aufschlägt. Ohne diese Unterscheidung würde ein nicht erkanntes Datum als das
     * heutige gebucht, ohne dass es jemand merkt.
     */
    private boolean dateKnown;
    /** Dasselbe für Kauf/Verkauf/Dividende: ohne erkannte Art ist kein Knopf vorgewählt. */
    private boolean actionKnown;

    /**
     * Das Zahlenfeld, in dem der Nutzer gerade steht — dort schreibt die Rechnung nicht hinein.
     *
     * <p>Ohne diese Sperre ließe sich eine vorbelegte Steuer nicht löschen: mit dem letzten gelöschten
     * Zeichen gilt das Feld als frei, die Rechnung setzt sofort wieder den Steuersatz hinein, und wer
     * eine 0 eintragen will, kommt nie dazu. Beim Verlassen des Feldes greift die Vorbelegung wieder —
     * dann ist es eine Hilfe und keine Bevormundung.</p>
     */
    private Field focusedField;

    /** Diese Bewegung steht schon im Depot — geht so an die Erkennungsliste zurück. */
    private boolean dupBooked;
    /**
     * Der Stand, für den zuletzt nach einer Doppelung gefragt wurde ({@code null} = noch nie). Solange
     * er sich nicht ändert, wird die Datenbank nicht erneut befragt — sonst liefe bei jedem Tastendruck
     * eine Abfrage.
     */
    private String lastDupKey;
    /** Der Hinweis, den die Erkennungsliste mitgab, und der Stand, für den er galt. */
    private int listHint;
    private String listHintKey;

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleAction;
    private TextView actionHeading;
    /** Hinweis unter den Umschaltknöpfen, wenn die Abrechnung die Art nicht hergab. */
    private TextView actionHint;
    /** Gelber Hinweis darunter: dieselbe Buchung scheint es schon zu geben. */
    private TextView duplicateWarning;
    private TextView textSecurity;
    private TextInputLayout dateLayout;
    private TextInputEditText editDate;
    private TextInputLayout grossLayout;
    private TextInputLayout feeLayout;
    private TextInputLayout netLayout;
    private TextInputLayout priceLayout;
    private View sharesRow;
    private TextInputLayout accountLayout;
    private TextInputLayout feeCategoryLayout;
    private TextInputLayout incomeCategoryLayout;
    private PickerTextView editAccount;
    private PickerTextView editFeeCategory;
    private PickerTextView editIncomeCategory;
    /** Kategorieliste nach Ausgabe/Einnahme gruppiert – dieselbe wie in der Buchungsmaske. */
    private CategoryFilterAdapter categoryAdapter;
    private MaterialButton btnSave;
    private MaterialButton btnDelete;
    private MaterialButton btnShowStatement;
    private LinearLayout detailBox;
    private CalcKeyboardView calcKeyboard;

    private final Map<Field, TextInputEditText> numberFields = new EnumMap<>(Field.class);
    /** Felder, die der Nutzer selbst gefüllt hat – nur die übrigen darf die Rechnung überschreiben. */
    private final Set<Field> userSet = EnumSet.noneOf(Field.class);
    private Field lastComputed;
    private Field justEdited;
    /** Schützt vor Rückkopplung, während die Rechnung Felder beschreibt. */
    private boolean writingBack;
    /** Läuft gerade die Vorbelegung aus einer Abrechnung? Dann verdrängt kein Wert den anderen. */
    private boolean prefilling;
    private boolean conflict;
    /** Abrechnungstext der Sitzung; gesetzt, wenn die Maske aus einem eingelesenen PDF kam. */
    private String statementTextPath;
    private String statementIsin;
    /** Die noch nicht endgültig abgelegte Abrechnung; beim Speichern wird sie zum Beleg. */
    private java.io.File pendingStatement;
    /** Beleg-Tag einer bereits gespeicherten Abrechnung (aus der Notiz der Gegenbuchung). */
    private String savedStatementTag;
    /** Beschriftung des aus der Abrechnung gewählten Datums; sie wird zum Anker. */
    private String chosenDateLabel;
    /**
     * Die Regel hinter der Wahl. Zu einem Datum gibt es zwei Lesarten — die Beschriftung daneben und
     * die Spaltenüberschrift darüber —, und die Beschriftung allein sagt nicht, welche gemeint war.
     */
    private de.spahr.ausgaben.statement.AnchorRule chosenDateRule;
    /**
     * Die einmal gelesene Abrechnung. Ohne sie läse jeder Tipp aufs Datumsfeld das PDF neu ein, und
     * zwar auf dem Bedienfaden.
     */
    private de.spahr.ausgaben.pdf.PdfText statementText;
    /**
     * Die Felder, in die der Nutzer <b>selbst</b> geschrieben hat — nur aus ihnen wird gelernt.
     *
     * <p>Nicht zu verwechseln mit {@code userSet}: dort stehen auch die Werte, welche die Maske aus der
     * Abrechnung vorbelegt hat. Hier landet nur, was durch den Beobachter kam, und der schweigt bei jedem
     * programmatischen Schreiben ({@code writingBack}). Genau diese Unterscheidung ist der Punkt: die App
     * soll die Beschriftung zu einer Zahl suchen, die der Nutzer abgetippt hat — nicht zu einer, die sie
     * sich selbst vorgelegt hat.</p>
     */
    private final Set<Field> typedFields = EnumSet.noneOf(Field.class);
    /** Hat der Nutzer das Datum selbst gewählt? Dann gehört auch dessen Beschriftung gelernt. */
    private boolean dateTyped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_security_tx);

        depot = orEmpty(getIntent().getStringExtra(EXTRA_DEPOT));
        kmyId = orEmpty(getIntent().getStringExtra(EXTRA_KMY_ID));
        securityName = orEmpty(getIntent().getStringExtra(EXTRA_NAME));
        repository = new Repository(this);
        taxRate = new SettingsStore(this).getDividendTaxPercent() / 100.0;
        batchMode = getIntent().getBooleanExtra(EXTRA_BATCH, false);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toggleAction = findViewById(R.id.toggleAction);
        actionHeading = findViewById(R.id.actionHeading);
        actionHint = findViewById(R.id.actionHint);
        duplicateWarning = findViewById(R.id.duplicateWarning);
        textSecurity = findViewById(R.id.textSecurity);
        textSecurity.setText(securityName);
        dateLayout = findViewById(R.id.dateLayout);
        editDate = findViewById(R.id.editDate);
        grossLayout = findViewById(R.id.grossLayout);
        feeLayout = findViewById(R.id.feeLayout);
        netLayout = findViewById(R.id.netLayout);
        priceLayout = findViewById(R.id.priceLayout);
        accountLayout = findViewById(R.id.accountLayout);
        feeCategoryLayout = findViewById(R.id.feeCategoryLayout);
        incomeCategoryLayout = findViewById(R.id.incomeCategoryLayout);
        editAccount = findViewById(R.id.editAccount);
        editFeeCategory = findViewById(R.id.editFeeCategory);
        editIncomeCategory = findViewById(R.id.editIncomeCategory);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        sharesRow = findViewById(R.id.sharesRow);
        btnShowStatement = findViewById(R.id.btnShowStatement);
        btnShowStatement.setOnClickListener(v -> showStatement());
        detailBox = findViewById(R.id.detailBox);
        calcKeyboard = findViewById(R.id.calcKeyboard);
        // Im Hochformat haelt der Platzhalter die Hoehe der Tastatur frei, damit das Formular
        // wie bisher ueber ihr endet; quer schwebt sie darueber und der Platzhalter bleibt weg.
        calcKeyboard.reserveSpaceWith(findViewById(R.id.calcSpacer));

        numberFields.put(Field.SHARES, findViewById(R.id.editShares));
        numberFields.put(Field.PRICE, findViewById(R.id.editPrice));
        numberFields.put(Field.GROSS, findViewById(R.id.editGross));
        numberFields.put(Field.FEE, findViewById(R.id.editFee));
        numberFields.put(Field.NET, findViewById(R.id.editNet));

        // Der Hinweis am Datumsfeld soll das Kalendersymbol nicht verdrängen – sonst verschwände mit
        // ihm der Weg, den Mangel zu beheben.
        dateLayout.setErrorIconDrawable(null);
        editDate.setOnClickListener(v -> showDatePicker());
        // Das Kalendersymbol liegt über dem Feld und würde den Tipper sonst schlucken.
        dateLayout.setEndIconOnClickListener(v -> showDatePicker());

        toggleAction.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                actionKnown = true;
                actionHint.setVisibility(View.GONE);
                applyAction();
                loadCategoryFavorites();
                // Gegenkonto und Kategorien hängen an der Aktion: eine Dividende wird über eine
                // Ertragskategorie gebucht, ein Kauf über eine Gebührenkategorie. Der Listener greift
                // auch bei programmatischem Setzen – damit deckt er die aus dem PDF erkannte Aktion mit
                // ab. Bei einer geladenen Bewegung bleibt es bei ihren gespeicherten Werten.
                // In der Erkennungsliste bringt der Eintrag Konto und Kategorien schon mit – dort
                // stünde die Nachfrage gegen das, was der Nutzer eben erst berichtigt hat.
                if (loaded == null && !batchMode) {
                    loadDefaults(currentAction(), true);
                }
                recompute(null);
            }
        });
        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        loadPickers();

        statementTextPath = getIntent().getStringExtra(EXTRA_STATEMENT_TEXT);
        statementIsin = getIntent().getStringExtra(EXTRA_STATEMENT_ISIN);
        String staged = getIntent().getStringExtra(EXTRA_STATEMENT_FILE);
        if (staged != null) {
            pendingStatement = new java.io.File(staged);
        }
        fromStatement = statementTextPath != null || pendingStatement != null;
        updateStatementButton();

        long txId = getIntent().getLongExtra(EXTRA_TX_ID, -1);
        if (txId >= 0) {
            repository.getSecurityTx(txId, this::bind);
        } else {
            setupNewMode();
        }
    }

    // ---- Modi ----

    private void setupNewMode() {
        toolbar.setTitle(batchMode ? R.string.security_tx_edit_title : R.string.security_tx_new_title);
        if (batchMode) {
            // Hier wird nichts gebucht: der Eintrag geht berichtigt an die Liste zurück, und erst dort
            // entscheidet „Alle speichern" über den ganzen Stapel.
            btnSave.setText(R.string.statement_batch_apply);
        }
        // Ohne Abrechnung ist der Kauf die richtige Annahme, und heute das richtige Datum – dort gibt es
        // keine Quelle, der man widersprechen könnte. Kam die Maske dagegen aus einem Dokument, wird
        // nichts vorgewählt, was nicht darin stand; ergänzt wird gleich darauf aus der Auslese.
        selectedDate.setTime(new Date());
        if (fromStatement) {
            clearDateField();
        } else {
            toggleAction.check(R.id.btnBuy);
            updateDateField();
        }
        applyAction();
        wireNumberFields();
        applyPrefill();
    }

    /**
     * Übernimmt, was aus einer eingelesenen Abrechnung erkannt wurde. Die Werte gelten wie selbst
     * eingetippt — die Rechnung darf sie nicht überschreiben, sie stehen ja so im Dokument. Was nicht
     * erkannt wurde, bleibt leer; <b>geraten wird nichts</b>.
     */
    private void applyPrefill() {
        android.content.Intent in = getIntent();
        String action = in.getStringExtra(EXTRA_PREFILL_ACTION);
        Integer button = action == null ? null : buttonFor(action);
        if (button != null) {
            toggleAction.check(button);
        }
        long date = in.getLongExtra(EXTRA_PREFILL_DATE, -1);
        if (date > 0) {
            selectedDate.setTimeInMillis(date);
            updateDateField();
        }
        if (!DIVIDEND.equals(currentAction())) {
            prefillNumber(Field.SHARES, in.hasExtra(EXTRA_PREFILL_SHARES)
                    ? in.getDoubleExtra(EXTRA_PREFILL_SHARES, 0) : null);
            prefillNumber(Field.PRICE, in.hasExtra(EXTRA_PREFILL_PRICE)
                    ? in.getDoubleExtra(EXTRA_PREFILL_PRICE, 0) : null);
        }
        // Auch bei Kauf und Verkauf, wo das Feld verborgen ist: es rechnet dort mit, und eine von Hand
        // angelegte Brutto-Regel liest gerade dort den umgerechneten Betrag eines Dollar-Papiers.
        prefillMoney(Field.GROSS, in.hasExtra(EXTRA_PREFILL_GROSS)
                ? in.getLongExtra(EXTRA_PREFILL_GROSS, 0) : null);
        prefillMoney(Field.FEE, in.hasExtra(EXTRA_PREFILL_FEE)
                ? in.getLongExtra(EXTRA_PREFILL_FEE, 0) : null);
        prefillMoney(Field.NET, in.hasExtra(EXTRA_PREFILL_NET)
                ? in.getLongExtra(EXTRA_PREFILL_NET, 0) : null);
        // Nichts vorgewählt und nichts erkannt: dann fehlt die Art, und das gehört gesagt.
        actionHint.setVisibility(actionKnown ? View.GONE : View.VISIBLE);
        // Was die Liste schon weiß, sagt die Maske sofort mit – die eigene Prüfung braucht eine Runde
        // über die Datenbank, und so lange stünde hier sonst nichts.
        listHint = in.getIntExtra(EXTRA_DUPLICATE, 0);
        dupBooked = listHint == R.string.statement_dup_booked;
        prefillPicker(editAccount, in.getStringExtra(EXTRA_PREFILL_ACCOUNT));
        prefillPicker(editFeeCategory, in.getStringExtra(EXTRA_PREFILL_FEE_CATEGORY));
        prefillPicker(editIncomeCategory, in.getStringExtra(EXTRA_PREFILL_INCOME_CATEGORY));
        // Die Werte stammen aus dem Dokument und stehen fest – der Stückpreis der Bank ist genauer als
        // einer, den die Maske aus Summe und Stückzahl zurückrechnet.
        prefilling = true;
        recompute(null);
        prefilling = false;
    }

    /**
     * Setzt ein Feld aus der Abrechnung. Der Beobachter bleibt dabei stumm: liefe er mit, rechnete die
     * Maske nach jedem einzelnen Feld neu, und beim letzten wäre die Stück-Gruppe überbestimmt — dann
     * gäbe der Stückpreis nach und würde durch einen zurückgerechneten ersetzt (164,0401 statt der 164,04
     * aus dem Dokument). Gerechnet wird deshalb erst am Ende, in einem Durchgang.
     */
    private void prefillNumber(Field field, Double value) {
        if (value != null) {
            writingBack = true;
            numberFields.get(field).setText(field == Field.SHARES
                    ? MoneyFormat.shares(value) : MoneyFormat.decimal(value, 0, 4));
            writingBack = false;
            userSet.add(field);
        }
    }

    /** Konto oder Kategorie aus dem Eintrag der Erkennungsliste; leer bleibt leer. */
    private void prefillPicker(PickerTextView field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            field.setText(value, false);
        }
    }

    private void prefillMoney(Field field, Long cents) {
        if (cents != null) {
            writingBack = true;
            numberFields.get(field).setText(MoneyFormat.plain(cents));
            writingBack = false;
            userSet.add(field);
        }
    }

    private void bind(SecurityTx tx) {
        if (tx == null) {
            finish();
            return;
        }
        loaded = tx;
        // Nur was noch nicht in der Datei steht, lässt sich hier ändern; alles andere gehört KMyMoney.
        readOnly = !tx.pending;
        selectedDate.setTimeInMillis(tx.date);
        updateDateField();

        Integer button = buttonFor(tx.action);
        if (button != null) {
            toggleAction.check(button);
        }
        // Ein-/Ausbuchung oder Wiederanlage: dafür gibt es keinen Umschalter – nur die Überschrift.
        toggleAction.setVisibility(button != null && !readOnly ? View.VISIBLE : View.GONE);
        if (button == null || readOnly) {
            actionHeading.setVisibility(View.VISIBLE);
            actionHeading.setText(actionLabel(tx.action));
            actionHeading.setTextColor(amountColor(tx.action));
        }
        applyAction();

        writingBack = true;
        boolean dividend = DIVIDEND.equals(tx.action);
        // Bei einer Dividende bleiben Anzahl und je Stück leer: die Stückzahl am Ex-Tag ist nicht bekannt.
        double count = dividend ? 0 : Math.abs(tx.shares);
        if (count > 0) {
            setNumber(Field.SHARES, count);
            setNumber(Field.PRICE, tx.amountCents / 100.0 / count);
        }
        setMoney(Field.GROSS, tx.amountCents);
        setMoney(Field.FEE, dividend ? tx.amountCents - tx.netCents : tx.feeCents);
        setMoney(Field.NET, dividend ? tx.netCents : totalOf(tx));
        writingBack = false;

        editAccount.setText(tx.moneyAccount, false);
        editFeeCategory.setText(tx.feeCategory, false);
        editIncomeCategory.setText(tx.incomeCategory, false);
        loadSavedStatement(tx.bookingId);

        if (readOnly) {
            applyReadOnly();
        } else {
            toolbar.setTitle(R.string.security_tx_edit_title);
            btnDelete.setVisibility(View.VISIBLE);
            // Alles Geladene gilt als gesetzt – sonst würde die erste Rechnung es überschreiben.
            userSet.addAll(numberFields.keySet());
            wireNumberFields();
        }
    }

    /** Belastung bzw. Gutschrift einer Kauf-/Verkaufsbuchung (Betrag plus/minus Gebühr). */
    private static long totalOf(SecurityTx tx) {
        return SELL.equals(tx.action) ? tx.amountCents - tx.feeCents : tx.amountCents + tx.feeCents;
    }

    /** Reine Ansicht: alle Felder gesperrt, keine Knöpfe, leere Felder fallen weg. */
    private void applyReadOnly() {
        toolbar.setTitle(actionLabel(loaded.action));
        btnSave.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        for (TextInputEditText f : numberFields.values()) {
            lockField(f);
        }
        lockField(editDate);
        dateLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        lockDropdown(editAccount, accountLayout);
        lockDropdown(editFeeCategory, feeCategoryLayout);
        lockDropdown(editIncomeCategory, incomeCategoryLayout);
        hideIfEmpty(accountLayout, editAccount);
        hideIfEmpty(feeCategoryLayout, editFeeCategory);
        hideIfEmpty(incomeCategoryLayout, editIncomeCategory);
        hideIfEmpty(feeLayout, numberFields.get(Field.FEE));
        // Ein-/Ausbuchungen tragen in KMyMoney keinen Geldwert – dort bliebe nur die Stückzahl übrig.
        if (loaded.amountCents == 0) {
            grossLayout.setVisibility(View.GONE);
            netLayout.setVisibility(View.GONE);
            priceLayout.setVisibility(View.GONE);
        }
        if (loaded.pending) {
            detailRow(getString(R.string.security_tx_pending), "");
        }
    }

    private void lockField(TextInputEditText field) {
        field.setFocusable(false);
        field.setClickable(false);
        field.setKeyListener(null);
        field.setOnClickListener(null);
    }

    private void lockDropdown(PickerTextView field, TextInputLayout layout) {
        field.setFocusable(false);
        field.setOnClickListener(null);
        field.setAdapter(null);
        // Der Pfeil verspräche eine Auswahl, die es hier nicht gibt.
        layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
    }

    private void hideIfEmpty(TextInputLayout layout, TextInputEditText field) {
        if (textOf(field).trim().isEmpty()) {
            layout.setVisibility(View.GONE);
        }
    }

    private void hideIfEmpty(TextInputLayout layout, PickerTextView field) {
        if (field.getText() == null || field.getText().toString().trim().isEmpty()) {
            layout.setVisibility(View.GONE);
        }
    }

    private void detailRow(String label, String value) {
        detailBox.setVisibility(View.VISIBLE);
        TextView t = new TextView(this);
        t.setText(value.isEmpty() ? label : label + ": " + value);
        t.setTextSize(14f);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        t.setTextColor(getColor(R.color.grey_text));
        detailBox.addView(t);
    }

    // ---- Felder je Aktion ----

    /** Beschriftungen und Sichtbarkeit an die gewählte Aktion anpassen. */
    private void applyAction() {
        boolean dividend = DIVIDEND.equals(currentAction());
        priceLayout.setHint(getString(dividend
                ? R.string.security_tx_price_dividend : R.string.security_tx_price));
        feeLayout.setHint(getString(dividend ? R.string.security_tx_tax : R.string.security_tx_fee));
        netLayout.setHint(getString(dividend ? R.string.security_tx_net : R.string.security_tx_total));
        feeCategoryLayout.setHint(getString(dividend
                ? R.string.security_tx_tax_category : R.string.security_tx_fee_category));
        // Der Bruttobetrag ist nur bei einer Dividende ein eigenes Feld – bei Kauf/Verkauf steckt er
        // zwischen Anzahl × Stückpreis und der Gesamtsumme und wäre eine dritte Zahl für dieselbe Sache.
        grossLayout.setVisibility(dividend ? View.VISIBLE : View.GONE);
        incomeCategoryLayout.setVisibility(dividend ? View.VISIBLE : View.GONE);
        // Umgekehrt bei Anzahl und Dividende je Stück: die Stückzahl, auf die eine Ausschüttung entfällt,
        // ist der Bestand am Ex-Tag – und der steht in der Abrechnung nicht. Beides wäre hier geraten,
        // deshalb gibt es die Felder bei einer Dividende gar nicht erst.
        sharesRow.setVisibility(dividend ? View.GONE : View.VISIBLE);
        moveTotalField(dividend);
        if (dividend) {
            userSet.remove(Field.SHARES);
            userSet.remove(Field.PRICE);
            clearField(Field.SHARES);
            clearField(Field.PRICE);
        } else {
            userSet.remove(Field.GROSS);
        }
    }

    /**
     * Rückt die Gesamtsumme an den Platz, der zur Aktion passt.
     *
     * <p>Bei Kauf und Verkauf ist sie die Zahl, die man vom Beleg abliest – sie steht deshalb gleich
     * unter dem Datum, Anzahl und Stückpreis folgen darunter. Bei einer Dividende ist das Netto dagegen
     * das Ende einer Kette (Brutto minus Steuer) und bleibt an deren Ende stehen.</p>
     */
    private void moveTotalField(boolean dividend) {
        android.view.ViewGroup form = (android.view.ViewGroup) netLayout.getParent();
        if (form == null) {
            return;
        }
        View predecessor = dividend ? feeLayout : dateLayout;
        if (form.indexOfChild(netLayout) == form.indexOfChild(predecessor) + 1) {
            return;   // steht schon dort
        }
        // Erst aushängen, dann den Platz bestimmen: alles hinter dem alten Platz rückt sonst um eins vor,
        // und ein vorher berechneter Index läge eine Zeile zu tief.
        form.removeView(netLayout);
        form.addView(netLayout, form.indexOfChild(predecessor) + 1);
    }

    private void clearField(Field field) {
        writingBack = true;
        numberFields.get(field).setText("");
        writingBack = false;
    }

    private String currentAction() {
        if (loaded != null && (readOnly || buttonFor(loaded.action) == null)) {
            return loaded.action;
        }
        int id = toggleAction.getCheckedButtonId();
        if (id == R.id.btnSell) {
            return SELL;
        }
        return id == R.id.btnDividend ? DIVIDEND : BUY;
    }

    private static Integer buttonFor(String action) {
        if (BUY.equals(action)) {
            return R.id.btnBuy;
        }
        if (SELL.equals(action)) {
            return R.id.btnSell;
        }
        return DIVIDEND.equals(action) ? R.id.btnDividend : null;
    }

    // ---- Rechnen ----

    private void wireNumberFields() {
        for (Map.Entry<Field, TextInputEditText> e : numberFields.entrySet()) {
            wireCalcField(e.getKey(), e.getValue());
        }
    }

    /**
     * Bindet ein Zahlenfeld an die gemeinsame Rechentastatur und meldet jede Änderung an die Rechnung.
     * Ein Feld, in das der Nutzer schreibt, gilt fortan als von ihm gesetzt und wird nicht überschrieben;
     * leert er es wieder, gibt er es für die Rechnung frei.
     */
    private void wireCalcField(final Field field, final TextInputEditText input) {
        AmountField.prepareCalc(input);
        input.setShowSoftInputOnFocus(false);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusedField = field;
                calcKeyboard.attachTo(input);
                calcKeyboard.setOnOk(valid -> {
                    if (valid) {
                        input.clearFocus();
                    }
                });
                calcKeyboard.setVisibility(View.VISIBLE);
                CalcKeyboardView.hideSystemKeyboard(input);
            } else {
                calcKeyboard.setVisibility(View.GONE);
                if (focusedField == field) {
                    focusedField = null;
                    // Jetzt erst: wer das Feld leer verlässt, bekommt die Vorbelegung zurück; wer eine
                    // 0 hineingeschrieben hat, behält sie.
                    recompute(null);
                }
            }
        });
        input.addTextChangedListener(new SimpleWatcher(() -> {
            if (writingBack) {
                return;
            }
            if (textOf(input).trim().isEmpty()) {
                userSet.remove(field);
                typedFields.remove(field);
            } else {
                userSet.add(field);
                // Hierher kommt nur, was der Nutzer wirklich getippt hat – programmatisches Schreiben
                // hat oben schon abgedreht.
                typedFields.add(field);
            }
            recompute(field);
        }));
    }

    /** Ergänzt die fehlenden Zahlen und schreibt sie in die Felder, die der Nutzer nicht selbst gefüllt hat. */
    private void recompute(Field edited) {
        if (readOnly) {
            return;
        }
        justEdited = edited;
        SecurityAmounts.Input in = new SecurityAmounts.Input();
        in.action = currentAction();
        // Der Steuersatz ist eine Hilfe beim Eintippen von Hand. Für eine eingelesene Abrechnung ist er
        // die falsche Quelle: dort hat die Regel gesucht, und was sie nicht fand, wurde nicht abgezogen.
        // Sonst zeigt eine Dividende innerhalb des Freibetrags eine gerechnete Steuer, die nirgends steht.
        in.taxRate = fromStatement ? 0 : taxRate;
        in.lastComputed = lastComputed;
        in.justEdited = justEdited;
        in.keepGiven = prefilling;
        in.shares = userSet.contains(Field.SHARES) ? number(Field.SHARES) : null;
        in.price = userSet.contains(Field.PRICE) ? number(Field.PRICE) : null;
        in.grossCents = userSet.contains(Field.GROSS) ? money(Field.GROSS) : null;
        in.feeCents = userSet.contains(Field.FEE) ? money(Field.FEE) : null;
        in.netCents = userSet.contains(Field.NET) ? money(Field.NET) : null;

        SecurityAmounts.Result r = SecurityAmounts.solve(in);
        conflict = r.conflict;
        netLayout.setError(conflict ? getString(R.string.security_tx_conflict) : null);
        if (conflict) {
            // Auch hier prüfen: an einem widersprüchlichen Stand ist keine Doppelung zu erkennen, und
            // ein noch stehender Hinweis von vorhin verschwindet damit.
            checkDuplicate();
            return;
        }
        if (r.computed != null) {
            lastComputed = r.computed;
            // Das nachgebende Feld ist keine Nutzereingabe mehr, sonst bliebe es für immer stehen.
            userSet.remove(r.computed);
        }
        writingBack = true;
        writeUnset(Field.SHARES, r.shares == null ? null : MoneyFormat.shares(r.shares));
        writeUnset(Field.PRICE, r.price == null ? null : MoneyFormat.decimal(r.price, 0, 4));
        writeUnset(Field.GROSS, r.grossCents == null ? null : MoneyFormat.plain(r.grossCents));
        // Die stillschweigende 0 bei Kauf/Verkauf bleibt ungeschrieben: stünde „0,00" im Feld, verdeckte
        // sie die Beschriftung, und wer dann hineintippt, schreibt vor oder hinter die Null statt sie zu
        // ersetzen. Bei einer Dividende ist die berechnete Steuer dagegen eine echte Auskunft.
        if (DIVIDEND.equals(in.action) || userSet.contains(Field.FEE)) {
            writeUnset(Field.FEE, r.feeCents == null ? null : MoneyFormat.plain(r.feeCents));
        }
        writeUnset(Field.NET, r.netCents == null ? null : MoneyFormat.plain(r.netCents));
        writingBack = false;
        checkDuplicate();
    }

    /**
     * Gibt es diese Buchung schon? Der Hinweis steht gleich unter der Auswahl der Art und hält nichts
     * auf — zweimal am selben Tag dasselbe Papier zum selben Preis zu kaufen ist selten, aber möglich.
     *
     * <p>Gefragt wird nur, wenn sich an Art, Datum oder Beträgen etwas geändert hat: sonst liefe bei
     * jedem Tastendruck eine Abfrage. Beim Bearbeiten einer gespeicherten Bewegung nimmt {@code exceptId}
     * sie selbst aus, sonst meldete sie sich als ihre eigene Doppelung.</p>
     */
    private void checkDuplicate() {
        if (readOnly) {
            return;
        }
        SecurityTx candidate = duplicateCandidate();
        String key = candidate == null ? "" : candidate.depot + "|" + candidate.securityKmyId + "|"
                + candidate.action + "|" + candidate.date + "|" + candidate.shares + "|"
                + candidate.amountCents + "|" + candidate.netCents + "|" + candidate.feeCents;
        if (key.equals(lastDupKey)) {
            return;
        }
        lastDupKey = key;
        if (listHintKey == null) {
            // Der erste Stand ist der, für den der Hinweis der Liste galt.
            listHintKey = key;
            showDuplicate(listHint);
        }
        if (candidate == null) {
            dupBooked = false;
            showDuplicate(key.equals(listHintKey) ? listHint : 0);
            return;
        }
        repository.findExistingSecurityTx(java.util.Collections.singletonList(candidate),
                loaded == null ? 0 : loaded.id, found -> {
                    if (!key.equals(lastDupKey)) {
                        return; // Zwischenzeitlich weitergetippt; die spätere Antwort gilt.
                    }
                    dupBooked = found[0];
                    if (dupBooked) {
                        showDuplicate(R.string.statement_dup_booked);
                    } else {
                        // Die Doppelung innerhalb der Auswahl kennt nur die Liste; sie gilt weiter,
                        // solange an den Werten nichts geändert wurde.
                        showDuplicate(key.equals(listHintKey) ? listHint : 0);
                    }
                });
    }

    private void showDuplicate(int hint) {
        duplicateWarning.setVisibility(hint == 0 ? View.GONE : View.VISIBLE);
        if (hint != 0) {
            duplicateWarning.setText(hint);
        }
    }

    /**
     * Die Bewegung, wie sie beim Speichern entstünde — aber nur so weit, wie {@code sameMovement} sie
     * vergleicht. {@code null}, solange noch etwas Wesentliches fehlt: an einem halben Stand ist keine
     * Doppelung zu erkennen.
     */
    private SecurityTx duplicateCandidate() {
        String action = currentAction();
        if (!actionKnown || !dateKnown || action == null || conflict || kmyId.isEmpty()) {
            return null;
        }
        Long gross = money(Field.GROSS);
        Long net = money(Field.NET);
        Double count = number(Field.SHARES);
        boolean dividend = DIVIDEND.equals(action);
        if (gross == null || net == null || gross <= 0 || (!dividend && (count == null || count <= 0))) {
            return null;
        }
        Long fee = money(Field.FEE);
        SecurityTx tx = new SecurityTx();
        tx.depot = depot;
        tx.securityKmyId = kmyId;
        tx.securityName = securityName;
        tx.date = selectedDate.getTimeInMillis();
        tx.action = action;
        tx.shares = dividend ? 0 : (SELL.equals(action) ? -Math.abs(count) : Math.abs(count));
        tx.amountCents = gross;
        tx.netCents = dividend ? net : gross;
        tx.feeCents = dividend ? 0 : Math.abs(fee == null ? 0 : fee);
        return tx;
    }

    /**
     * Schreibt einen berechneten Wert – aber nie in ein Feld, das der Nutzer selbst gefüllt hat, und
     * nie in das, in dem er gerade steht.
     */
    private void writeUnset(Field field, String text) {
        if (userSet.contains(field) || field == focusedField) {
            return;
        }
        TextInputEditText input = numberFields.get(field);
        String now = textOf(input);
        String next = text == null ? "" : text;
        if (!now.equals(next)) {
            input.setText(next);
        }
    }

    private void setNumber(Field field, double value) {
        // Stückzahlen feiner als Kurse: Sparplan-Anteile haben fünf Nachkommastellen.
        numberFields.get(field).setText(field == Field.SHARES
                ? MoneyFormat.shares(value) : MoneyFormat.decimal(value, 0, 4));
    }

    private void setMoney(Field field, long cents) {
        numberFields.get(field).setText(MoneyFormat.plain(cents));
    }

    private Long money(Field field) {
        String raw = textOf(numberFields.get(field)).trim();
        return raw.isEmpty() ? null : AmountExpression.toCents(raw);
    }

    private Double number(Field field) {
        String raw = textOf(numberFields.get(field)).trim().replace(',', '.');
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Auswahllisten ----

    private void loadPickers() {
        // Konto: Favoriten, dann die Konten der gewählten Gruppe, dann der Rest – mit Tastatur und Liste,
        // genau wie in der Buchungsmaske.
        repository.getAccountNames(names -> PickerAdapters.accounts(repository, editAccount, names));
        repository.getCategoriesGrouped(g -> {
            categoryAdapter = new CategoryFilterAdapter(this, null,
                    getString(R.string.category_group_expense), g.expense,
                    getString(R.string.category_group_income), g.income);
            PickerAdapters.categories(editFeeCategory, categoryAdapter);
            PickerAdapters.categories(editIncomeCategory, categoryAdapter);
            loadCategoryFavorites();
        });
    }

    /**
     * Die an diesem Wertpapier schon verwendeten Kategorien als Vorspann der Auswahlliste – dieselbe
     * Hilfe wie „bei diesem Empfänger" in der Buchungsmaske, nur bezogen auf das Wertpapier.
     *
     * <p>Beide Felder teilen sich einen Adapter, also auch den Vorspann. Gezeigt wird deshalb, was zur
     * gerade sichtbaren Aktion passt: bei einer Dividende die Ertragskategorien, sonst die Gebühren.</p>
     */
    private void loadCategoryFavorites() {
        if (categoryAdapter == null) {
            return;
        }
        repository.getSecurityUsedCategories(depot, kmyId, lists -> {
            if (categoryAdapter == null || lists.size() < 2) {
                return;
            }
            List<String> used = DIVIDEND.equals(currentAction()) ? lists.get(1) : lists.get(0);
            categoryAdapter.setFavorites(getString(R.string.category_group_security), used);
        });
    }

    /**
     * Gegenkonto und Kategorien aus der jüngsten Bewegung derselben Art übernehmen — zuerst von diesem
     * Wertpapier, sonst von einem beliebigen anderen (siehe {@code DepotRepository.getTxDefaults}).
     *
     * @param overwrite beim Aktionswechsel {@code true}: die Felder tragen dann noch die Werte der
     *                  vorherigen Aktion und sind damit überholt — sonst bliebe die Gebührenkategorie
     *                  eines Kaufs stehen, obwohl daneben „Steuerkategorie" steht
     */
    private void loadDefaults(String action, boolean overwrite) {
        repository.getSecurityTxDefaults(depot, kmyId, action, last -> {
            // Die Abfrage lief über den Executor. Wer zweimal schnell umschaltet, bekommt die Antwort auf
            // die alte Frage womöglich nach der neuen – dann ist sie überholt und wird verworfen.
            if (last == null || !action.equals(currentAction())) {
                return;
            }
            setDefault(editAccount, last.moneyAccount, overwrite);
            setDefault(editFeeCategory, last.feeCategory, overwrite);
            setDefault(editIncomeCategory, last.incomeCategory, overwrite);
        });
    }

    private void setDefault(PickerTextView field, String value, boolean overwrite) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (overwrite || textOf(field).trim().isEmpty()) {
            field.setText(value, false);
        }
    }

    // ---- Speichern ----

    private void save() {
        if (batchMode) {
            returnToList();
            return;
        }
        if (conflict) {
            Toast.makeText(this, R.string.security_tx_conflict, Toast.LENGTH_LONG).show();
            return;
        }
        if (!actionKnown) {
            Toast.makeText(this, R.string.security_tx_need_action, Toast.LENGTH_LONG).show();
            return;
        }
        if (!dateKnown) {
            Toast.makeText(this, R.string.security_tx_need_date, Toast.LENGTH_LONG).show();
            return;
        }
        String action = currentAction();
        boolean dividend = DIVIDEND.equals(action);
        Long gross = money(Field.GROSS);
        Long net = money(Field.NET);
        Double count = number(Field.SHARES);
        if (gross == null || net == null || gross <= 0 || (!dividend && (count == null || count <= 0))) {
            Toast.makeText(this, R.string.security_tx_need_amounts, Toast.LENGTH_LONG).show();
            return;
        }
        String account = textOf(editAccount).trim();
        if (account.isEmpty()) {
            Toast.makeText(this, R.string.security_tx_need_account, Toast.LENGTH_LONG).show();
            return;
        }
        Long fee = money(Field.FEE);
        long feeCents = fee == null ? 0 : Math.abs(fee);

        SecurityTx tx = loaded != null ? loaded : new SecurityTx();
        tx.depot = depot;
        tx.securityKmyId = kmyId;
        tx.securityName = securityName;
        tx.date = selectedDate.getTimeInMillis();
        tx.action = action;
        // Eine Dividende bewegt keine Stücke; die Maske fragt dort auch keine Anzahl mehr ab, weil der
        // Bestand am Ex-Tag nicht in der Abrechnung steht.
        tx.shares = dividend ? 0 : (SELL.equals(action) ? -Math.abs(count) : Math.abs(count));
        tx.amountCents = gross;
        tx.netCents = dividend ? net : gross;
        tx.feeCents = dividend ? 0 : feeCents;
        tx.moneyAccount = account;
        tx.feeCategory = textOf(editFeeCategory).trim();
        tx.incomeCategory = dividend ? textOf(editIncomeCategory).trim() : "";

        Booking booking = buildBooking(action, account, net);
        // Die Abrechnung wird zum Beleg der Gegenbuchung – dauerhaft und über denselben Weg wie die
        // Belege der übrigen Buchungen (Ablage, Jahresordner, Abgleich, Export).
        if (pendingStatement != null) {
            booking.note = de.spahr.ausgaben.receipt.SingleReceipt.attach(
                    this, pendingStatement, booking.note, booking.createdAt);
            pendingStatement = null;
            // Wohin die Datei gewandert ist, steht jetzt nur noch in der Notiz. Der Tag wird gebraucht:
            // gleich danach wird aus dieser Abrechnung gelernt und an ihr nachgeprüft.
            savedStatementTag = de.spahr.ausgaben.receipt.NoteReceipt.pdfName(booking.note);
        }
        final Double sharesGiven = number(Field.SHARES);
        final Double priceGiven = number(Field.PRICE);
        final Long feeGiven = money(Field.FEE);
        final Long netGiven = money(Field.NET);
        Runnable done = () -> {
            Toast.makeText(this, R.string.security_tx_saved, Toast.LENGTH_SHORT).show();
            offerToLearn(action, sharesGiven, priceGiven, feeGiven, netGiven);
        };
        if (loaded != null) {
            repository.updateManualSecurityTx(tx, booking, done);
        } else {
            repository.saveManualSecurityTx(tx, booking, done);
        }
    }

    /**
     * Der Weg zurück in die Erkennungsliste. Übergeben wird der Stand der Maske, wie er dasteht — auch
     * ein unfertiger: geprüft wird in der Liste, und dort bleibt die Zeile dann eben rot. Wer beim
     * Berichtigen zwischendurch aufhört, soll das Erreichte nicht verlieren.
     */
    private void returnToList() {
        String action = currentAction();
        android.content.Intent out = new android.content.Intent();
        // Was hier nicht feststeht, wird auch nicht übergeben: der Eintrag bleibt in der Liste rot,
        // statt über den Umweg durch die Maske stillschweigend das heutige Datum zu erben.
        if (actionKnown) {
            out.putExtra(EXTRA_PREFILL_ACTION, action);
        }
        if (dateKnown) {
            out.putExtra(EXTRA_PREFILL_DATE, selectedDate.getTimeInMillis());
        }
        putNumber(out, EXTRA_PREFILL_SHARES, number(Field.SHARES));
        putNumber(out, EXTRA_PREFILL_PRICE, number(Field.PRICE));
        putMoney(out, EXTRA_PREFILL_GROSS, money(Field.GROSS));
        putMoney(out, EXTRA_PREFILL_FEE, money(Field.FEE));
        putMoney(out, EXTRA_PREFILL_NET, money(Field.NET));
        out.putExtra(EXTRA_PREFILL_ACCOUNT, textOf(editAccount).trim());
        out.putExtra(EXTRA_PREFILL_FEE_CATEGORY, textOf(editFeeCategory).trim());
        out.putExtra(EXTRA_PREFILL_INCOME_CATEGORY,
                DIVIDEND.equals(action) ? textOf(editIncomeCategory).trim() : "");
        out.putExtra(EXTRA_CONFLICT, conflict);
        out.putExtra(EXTRA_DUP_BOOKED, dupBooked);
        setResult(RESULT_OK, out);
        finish();
    }

    private static void putNumber(android.content.Intent out, String key, Double value) {
        if (value != null) {
            out.putExtra(key, (double) value);
        }
    }

    private static void putMoney(android.content.Intent out, String key, Long value) {
        if (value != null) {
            out.putExtra(key, (long) value);
        }
    }

    /**
     * Kam die Maske aus einer eingelesenen Abrechnung, leitet die App jetzt ab, wo die Werte darin
     * standen — und fragt einmal, ob sie sich das für diese Bank merken soll.
     *
     * <p>Das ist der Kern des Verfahrens: die erste Abrechnung einer Bank tippt man ohnehin ab, und
     * genau daraus lernt die App die Beschriftungen. Eine Markier-Oberfläche, in der man auf dem Handy
     * kleine Zahlen antippt, wird damit überflüssig.</p>
     */
    private void offerToLearn(String action, Double shares, Double price, Long feeCents, Long netCents) {
        if (statementTextPath == null && statementPdf() == null) {
            finish();
            return;
        }
        // Wer nichts angefasst hat, hat der App nichts beizubringen – dann bleibt die Rückfrage aus.
        // Das steht vor dem Einlesen: sonst läge das PDF umsonst auf dem Tisch.
        if (typedFields.isEmpty() && !dateTyped) {
            finish();
            return;
        }
        // Das Einlesen ist keine Kleinigkeit mehr, seit es aus dem PDF selbst kommt – also nicht im
        // Vordergrund. Gelernt wird gleich mit; der Dialog kommt danach auf dem Bedienfaden.
        repository.executor().execute(() -> {
            final de.spahr.ausgaben.pdf.PdfText text = readStatementText();
            runOnUiThread(() -> {
                // Wer inzwischen weggegangen ist, bekommt keinen Dialog mehr auf ein Fenster, das es
                // nicht mehr gibt.
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (text == null) {
                    finish();
                    return;
                }
                learnFrom(text, action, shares, price, feeCents, netCents);
            });
        });
    }

    /** Der zweite Teil von {@link #offerToLearn}: die Abrechnung liegt gelesen vor. */
    private void learnFrom(de.spahr.ausgaben.pdf.PdfText text, String action, Double shares,
                           Double price, Long feeCents, Long netCents) {
        final StatementTemplates store = new StatementTemplates(this);
        final StatementTemplate existing = store.match(text, depot);

        // Wieviel gelernt wird, hängt daran, ob es für diese Bank schon eine Vorlage gibt.
        //
        // <b>Noch keine:</b> dann zählt jeder Wert der Maske, auch ein gerechneter. Bei einer Dividende
        // tippt man Brutto und Steuer, und das Netto rechnet die Maske – ohne diesen Fall entstünde eine
        // Vorlage ohne Gesamtbetrag-Regel, und die erkennt kein Dokument wieder ({@code score}).
        //
        // <b>Schon eine:</b> dann nur das selbst Getippte. Alles andere hat die Vorlage vorgelegt, und
        // dazu erneut eine Beschriftung zu suchen hieße, den eigenen Vorschlag wiederzufinden und für
        // eine Bestätigung zu halten.
        final boolean ersteVorlage = existing == null;
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = action;
        // Bei einer Dividende gibt es weder Stückzahl noch Stückpreis: die Anzahl am Ex-Tag steht nicht in
        // der Abrechnung, und die Ausschüttung je Stück ist dort in Fremdwährung ausgewiesen.
        known.shares = DIVIDEND.equals(action) || !lernen(ersteVorlage, Field.SHARES) ? null : shares;
        known.price = DIVIDEND.equals(action) || !lernen(ersteVorlage, Field.PRICE) ? null : price;
        known.feeCents = lernen(ersteVorlage, Field.FEE) ? feeCents : null;
        known.netCents = lernen(ersteVorlage, Field.NET) ? netCents : null;
        // Wird aus der Gebühr eine feste Ordergebühr, braucht sie eine Kategorie – und die steht hier.
        known.feeCategory = textOf(editFeeCategory).trim();
        // Beim Datum zählt die eigene Wahl: hat der Nutzer sie nicht getroffen, bleibt die schon gelernte
        // Beschriftung gültig. Ohne das würde bei zwei Zeilen mit demselben Datum („Zahltag" und
        // „Valuta") jedes Mal die unterste neu gelernt.
        known.dateMillis = ersteVorlage || dateTyped ? selectedDate.getTimeInMillis() : -1;
        known.dateAnchor = chosenDateLabel;
        known.dateRule = chosenDateRule;

        if (known.dateAnchor == null && existing != null
                && existing.rule(StatementTemplate.Field.DATE) != null) {
            known.dateAnchor = existing.rule(StatementTemplate.Field.DATE).anchors.get(0);
        }
        final StatementTemplate raw = TemplateLearner.learn(text, known);
        // Zwei Lesarten dessen, was dabei herauskam:
        // „Ersetzen" – die neue Regel gilt, das bisher Gelernte bleibt nur, wo diese Abrechnung nichts
        //   hergab (eine fehlende Zeile). Sonst verlernte die App an einer unvollständigen Abrechnung.
        // „Hinzufügen" – die bisherige Reihenfolge behält Vorrang, die neue Beschriftung kommt als
        //   weiterer Rückfall dahinter. Das schützt, was auf der Regelseite von Hand geordnet wurde.
        final StatementTemplate replaced = raw.mergedOver(existing);
        final StatementTemplate appended = raw.appendedTo(existing, text);
        // Nur fragen, wenn dabei wirklich etwas Neues herauskam. Wer nichts korrigiert hat, bekommt
        // dieselben Regeln zurück – dann gibt es nichts zu merken, und die Rückfrage wäre nur Lärm.
        // Dasselbe, wenn der korrigierte Wert im PDF gar nicht vorkommt: dann entsteht keine Regel.
        if (replaced.isEmpty() || replaced.sameAs(existing)) {
            finish();
            return;
        }
        AppDialog dialog = new AppDialog(this);
        dialog.setTitle(R.string.statement_learn_title);
        if (appended.sameAs(replaced)) {
            dialog.setMessage(learnMessage(raw, existing));
            dialog.setPositiveButton(R.string.statement_learn_yes,
                    (d, w) -> keep(store, replaced, text));
        } else {
            // Es gibt einen echten Widerspruch: die neue Beschriftung tritt an die Stelle einer
            // vorhandenen. Das ist nicht zu entscheiden, ohne zu wissen, ob die alte weiter gebraucht
            // wird – also wird gefragt, statt zu raten.
            dialog.setMessage(R.string.statement_learn_conflict);
            dialog.setPositiveButton(R.string.statement_learn_append,
                    (d, w) -> keep(store, appended, text));
            dialog.setNeutralButton(R.string.statement_learn_replace,
                    (d, w) -> keep(store, replaced, text));
        }
        dialog.setNegativeButton(R.string.cancel, (d, w) -> finish());
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    /** Ob dieses Feld in den Lernvorgang geht (siehe {@code offerToLearn}). */
    private boolean lernen(boolean ersteVorlage, Field field) {
        return ersteVorlage || typedFields.contains(field);
    }

    /**
     * Der Text der Rückfrage. Hat der Lerner eine <b>feste Ordergebühr</b> erschlossen, sagt er das mit
     * dem Betrag.
     *
     * <p>Das ist der einzige Wert, den die App nicht im Dokument gefunden, sondern aus einer Differenz
     * gefolgert hat: der Gesamtbetrag stand nicht darin, der Betrag ohne die Gebühr schon. Ein solcher
     * Wert darf nicht stillschweigend entstehen — wer später auf der Regelseite eine Gebühr vorfindet,
     * die er nie eingetragen hat, rätselt sonst, woher sie kommt.</p>
     */
    private String learnMessage(StatementTemplate raw, StatementTemplate existing) {
        boolean neu = raw.fixedFeeCents > 0
                && (existing == null || existing.fixedFeeCents != raw.fixedFeeCents);
        return neu
                ? getString(R.string.statement_learn_fixed_fee, MoneyFormat.plain(raw.fixedFeeCents))
                : getString(R.string.statement_learn_message);
    }

    /** Die gewählte Fassung merken – und sie sogleich an derselben Abrechnung nachprüfen. */
    private void keep(StatementTemplates store, StatementTemplate template,
                      de.spahr.ausgaben.pdf.PdfText text) {
        store.save(template, depot);
        // Auch die Zuordnung merken – dann findet die nächste Abrechnung das Wertpapier selbst dann,
        // wenn die ISIN in KMyMoney nicht gepflegt ist.
        store.rememberSecurity(statementIsin, depot, kmyId, securityName);
        Toast.makeText(this, R.string.statement_learned, Toast.LENGTH_SHORT).show();
        verifyLearned(template, text);
    }

    /**
     * Die Probe aufs Gemerkte: liest die eben gespeicherte Vorlage diese Abrechnung so, wie sie am Ende
     * in der Maske stand?
     *
     * <p>Bisher zeigte sich das erst bei der nächsten Abrechnung dieser Bank – Wochen später, vor einer
     * wieder leeren Maske und ohne Anhalt, woran es lag. Dabei liegt hier alles vor: die Regeln und das
     * Dokument. Stimmt es, bleibt die App still; sonst sagt sie, was nicht stimmt, und bietet den Weg
     * auf die Regelseite an – mit dieser Abrechnung schon als Probe.</p>
     */
    private void verifyLearned(StatementTemplate template, de.spahr.ausgaben.pdf.PdfText text) {
        java.util.List<TemplateCheck.Complaint> maengel =
                TemplateCheck.check(template, text, expectation(template.action));
        if (maengel.isEmpty()) {
            finish();
            return;
        }
        StringBuilder message = new StringBuilder(getString(R.string.statement_check_intro));
        for (TemplateCheck.Complaint c : maengel) {
            message.append("\n\n").append(complaintLine(c, template.action));
        }
        message.append("\n\n").append(getString(R.string.statement_check_hint));
        AppDialog dialog = new AppDialog(this);
        dialog.setTitle(R.string.statement_check_title);
        dialog.setMessage(message.toString());
        dialog.setPositiveButton(R.string.statement_check_rules, (d, w) -> openRules());
        dialog.setNegativeButton(android.R.string.ok, (d, w) -> finish());
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    /** Was in der Maske stand – der Sollwert der Nachprüfung. */
    private TemplateCheck.Expected expectation(String action) {
        TemplateCheck.Expected soll = new TemplateCheck.Expected();
        soll.action = action;
        soll.dateMillis = selectedDate.getTimeInMillis();
        soll.shares = number(Field.SHARES);
        soll.price = number(Field.PRICE);
        soll.feeCents = money(Field.FEE);
        soll.netCents = money(Field.NET);
        soll.grossCents = money(Field.GROSS);
        for (Field field : typedFields) {
            soll.typed.add(StatementTemplate.Field.valueOf(field.name()));
        }
        if (dateTyped) {
            soll.typed.add(StatementTemplate.Field.DATE);
        }
        return soll;
    }

    /** „Gebühr: erwartet 9,90, gelesen 4,95" – Feld, Soll und Ist in einer Zeile. */
    private String complaintLine(TemplateCheck.Complaint c, String action) {
        String name = StatementFieldNames.of(this, c.field, action);
        String soll = valueText(c.field, c.expected);
        switch (c.kind) {
            case NO_RULE:
                return getString(R.string.statement_check_norule, name, soll);
            case NOT_FOUND:
                return getString(R.string.statement_check_missing, name, soll);
            default:
                return getString(R.string.statement_check_wrong, name, soll,
                        valueText(c.field, c.actual));
        }
    }

    /** Ein Wert der Nachprüfung so geschrieben, wie ihn die Maske zeigt. */
    private String valueText(StatementTemplate.Field field, double value) {
        switch (field) {
            case DATE:
                return dateFormat.format(new Date((long) value));
            case SHARES:
                return MoneyFormat.shares(value);
            case PRICE:
                return MoneyFormat.decimal(value, 2, 4);
            default:
                return MoneyFormat.plain(Math.round(value * 100.0));
        }
    }

    /**
     * Auf die Regelseite – mit der Abrechnung als Probe.
     *
     * <p>Erst schließen, dann öffnen: die Buchung ist gespeichert, die Maske hat ihren Zweck erfüllt und
     * hat im Rückweg nichts mehr zu suchen.</p>
     */
    private void openRules() {
        android.content.Intent i = new android.content.Intent(this, StatementRulesActivity.class);
        i.putExtra(StatementRulesActivity.EXTRA_DEPOT, depot);
        java.io.File pdf = statementPdf();
        if (pdf != null) {
            try {
                i.setData(androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", pdf));
                i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Ohne Probe ist die Seite immer noch zu gebrauchen – die Regeln stehen dort so oder so.
            }
        }
        finish();
        startActivity(i);
    }

    /**
     * Die Abrechnung einlesen, aus der gelernt und an der nachgeprüft wird.
     *
     * <p>Zuerst aus dem <b>PDF selbst</b>. Der Zwischenspeicher trägt nur den Text, und der wird beim
     * Zurückbauen Wort für Wort mit einem Leerzeichen zusammengesetzt – die Wortpositionen sind dahin.
     * Genau daraus lebt aber die Spaltenregel: gelernt würde an einer Lage, die es im Dokument gar nicht
     * gibt, und beim nächsten Import läse dieselbe Regel etwas anderes. Der Text bleibt der Rückfall für
     * den Stapelweg, der nur ihn durchreicht.</p>
     *
     * @return {@code null}, wenn beides nicht mehr da ist
     */
    private de.spahr.ausgaben.pdf.PdfText readStatementText() {
        if (statementText != null) {
            return statementText;
        }
        statementText = liesAbrechnung();
        return statementText;
    }

    /** Das eigentliche Einlesen – siehe {@link #readStatementText()}. */
    private de.spahr.ausgaben.pdf.PdfText liesAbrechnung() {
        java.io.File pdf = statementPdf();
        if (pdf != null) {
            try {
                de.spahr.ausgaben.pdf.PdfText text = de.spahr.ausgaben.pdf.PdfTextExtractor.read(
                        this, android.net.Uri.fromFile(pdf));
                if (text.hasText()) {
                    return text;
                }
            } catch (Exception e) {
                // Dann eben aus dem Zwischenspeicher – besser als gar nicht zu lernen.
            }
        }
        if (statementTextPath == null) {
            return null;
        }
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(
                    new java.io.File(statementTextPath).toPath());
            return de.spahr.ausgaben.pdf.PdfText.fromLines(
                    new String(raw, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Die Abrechnung als Beleg ----

    /**
     * Öffnet die zugehörige Abrechnung in einem PDF-Betrachter — beim Eintippen der Werte hat man sie
     * damit nebenher offen, und später ist sie der Beleg zur Buchung.
     */
    private void showStatement() {
        java.io.File file = statementPdf();
        if (file == null) {
            Toast.makeText(this, R.string.receipt_error, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.receipt_pdf_no_viewer, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.receipt_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Die Abrechnung als Datei – die noch nicht abgelegte oder die schon abgelegte; {@code null}, wenn
     * es keine (mehr) gibt.
     */
    private java.io.File statementPdf() {
        if (pendingStatement != null && pendingStatement.exists()) {
            return pendingStatement;
        }
        if (savedStatementTag == null) {
            return null;
        }
        java.io.File file = statementFile();
        return file != null && file.exists() ? file : null;
    }

    /** Die gespeicherte Belegdatei zum Tag aus der Notiz; {@code null}, wenn sie nicht (mehr) lokal liegt. */
    private java.io.File statementFile() {
        java.util.List<String> pages = de.spahr.ausgaben.receipt.ReceiptPages.find(
                this, savedStatementTag, yearOf(selectedDate.getTimeInMillis()),
                de.spahr.ausgaben.receipt.NoteReceipt.PDF);
        if (pages.isEmpty()) {
            return null;
        }
        return de.spahr.ausgaben.receipt.Receipts.localFile(this, pages.get(0));
    }

    private static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }

    /** Den Knopf nur zeigen, wenn es wirklich eine Abrechnung zu sehen gibt. */
    private void updateStatementButton() {
        boolean available = pendingStatement != null || savedStatementTag != null;
        btnShowStatement.setVisibility(available ? View.VISIBLE : View.GONE);
    }

    /**
     * Sucht die angehängte Abrechnung einer gespeicherten Bewegung. Sie hängt an der Gegenbuchung — die
     * Bewegung selbst führt keine Notiz, die Buchung schon, und damit greift die vorhandene Belegablage
     * samt Abgleich und Export.
     */
    private void loadSavedStatement(long bookingId) {
        if (bookingId <= 0) {
            return;
        }
        repository.getBookingById(bookingId, b -> {
            if (b != null) {
                savedStatementTag = de.spahr.ausgaben.receipt.NoteReceipt.pdfName(b.note);
                updateStatementButton();
            }
        });
    }

    /**
     * Die Geldbuchung zur Bewegung: eine Umbuchung zwischen dem Geldkonto und dem Wertpapier – genau die
     * Form, die auch der KMyMoney-Import erzeugt. Beim Kauf verlässt das Geld das Konto, bei Verkauf und
     * Dividende kommt es an (bei der Dividende der <b>Nettobetrag</b>, denn nur der wird gutgeschrieben).
     */
    private Booking buildBooking(String action, String account, long netCents) {
        Booking b = new Booking();
        b.account = account;
        b.isTransfer = true;
        b.transferAccount = securityName;
        b.isIncome = !BUY.equals(action);
        b.amountCents = Math.abs(netCents);
        b.payee = securityName;
        b.createdAt = selectedDate.getTimeInMillis();
        b.category = "";
        return b;
    }

    private void confirmDelete() {
        if (loaded == null) {
            return;
        }
        AppDialog.destructive(this)
                .setTitle(R.string.security_tx_delete_title)
                .setMessage(R.string.security_tx_delete_message)
                .setPositiveButton(R.string.security_tx_delete, (d, w) ->
                        repository.deleteManualSecurityTx(loaded.id, () -> {
                            Toast.makeText(this, R.string.security_tx_deleted, Toast.LENGTH_SHORT).show();
                            finish();
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Kleinkram ----

    /**
     * Tipp aufs Datumsfeld. Kam die Maske aus einer Abrechnung, in der die App das gebuchte Datum noch
     * nicht kennt, legt sie erst die im Dokument gefundenen zur Auswahl vor — eine Abrechnung trägt
     * mehrere (Briefdatum, Ex-Tag, Zahltag, Valuta), und welches gemeint ist, wäre geraten. Die Wahl
     * bringt der Vorlage beim Speichern den Anker bei, ab dann kommt das Datum von selbst.
     */
    private void showDatePicker() {
        java.util.List<de.spahr.ausgaben.statement.StatementScan.DateCandidate> found = statementDates();
        if (found.isEmpty()) {
            showCalendar();
            return;
        }
        CharSequence[] labels = new CharSequence[found.size() + 1];
        for (int i = 0; i < found.size(); i++) {
            // Manche Beschriftungen tragen den Doppelpunkt schon („Datum:"), dann keinen zweiten.
            String label = found.get(i).label.trim();
            if (label.endsWith(":")) {
                label = label.substring(0, label.length() - 1).trim();
            }
            labels[i] = label + ":  " + dateFormat.format(new Date(found.get(i).millis));
        }
        labels[found.size()] = getString(R.string.statement_date_other);
        new AppDialog(this)
                .setTitle(R.string.statement_date_title)
                .setItems(labels, (d, which) -> {
                    if (which >= found.size()) {
                        showCalendar();
                        return;
                    }
                    selectedDate.setTimeInMillis(found.get(which).millis);
                    chosenDateLabel = found.get(which).label;
                    chosenDateRule = found.get(which).rule;
                    dateTyped = true;
                    updateDateField();
                })
                .show();
    }

    /**
     * Die Datumsangaben der eingelesenen Abrechnung — leer, wenn die Maske nicht aus einer stammt oder
     * die Vorlage das Datum bereits kennt (dann steht es schon im Feld).
     */
    private java.util.List<de.spahr.ausgaben.statement.StatementScan.DateCandidate> statementDates() {
        if (statementTextPath == null || readOnly) {
            return java.util.Collections.emptyList();
        }
        de.spahr.ausgaben.pdf.PdfText text = readStatementText();
        return text == null ? java.util.Collections.emptyList()
                : de.spahr.ausgaben.statement.StatementScan.dates(text);
    }

    private void showCalendar() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            // Von Hand gewählt: eine vorher angetippte Beschriftung meint jetzt ein anderes Datum.
            chosenDateLabel = null;
            chosenDateRule = null;
            dateTyped = true;
            updateDateField();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Schreibt das gewählte Datum ins Feld – damit steht es fest. */
    private void updateDateField() {
        dateKnown = true;
        editDate.setText(dateFormat.format(selectedDate.getTime()));
        dateLayout.setError(null);
    }

    /**
     * In der Abrechnung stand kein Datum, das die Vorlage kennt. Das Feld bleibt leer und sagt, warum –
     * ein Tipp legt die im Dokument gefundenen Angaben vor.
     */
    private void clearDateField() {
        dateKnown = false;
        editDate.setText("");
        dateLayout.setError(getString(R.string.statement_date_missing));
    }

    private String actionLabel(String action) {
        switch (action == null ? "" : action) {
            case BUY:
                return getString(R.string.action_buy);
            case SELL:
                return getString(R.string.action_sell);
            case DIVIDEND:
                return getString(R.string.action_dividend);
            case "add":
                return getString(R.string.action_add);
            case "remove":
                return getString(R.string.action_remove);
            case "reinvest":
                return getString(R.string.action_reinvest);
            default:
                return action == null ? "" : action;
        }
    }

    /** Verkauf/Ausbuchung = rot, Kauf/Wiederanlage/Einbuchung = grün, Dividende = Standardfarbe. */
    private int amountColor(String action) {
        switch (action == null ? "" : action) {
            case SELL:
            case "remove":
                return getColor(R.color.expense_red);
            case BUY:
            case "reinvest":
            case "add":
                return getColor(R.color.income_green);
            default:
                android.util.TypedValue tv = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
                return getColor(tv.resourceId);
        }
    }

    private static String textOf(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
