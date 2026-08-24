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
    /**
     * Bestand zum Buchungsdatum. Eine Dividende trägt selbst keine Stückzahl – die Liste kennt sie aus
     * allen Bewegungen und gibt sie mit, damit „je Stück" hier ohne zweite Abfrage stimmt.
     */
    public static final String EXTRA_SHARES_HELD = "sharesHeld";

    // ---- Vorbelegung aus einer eingelesenen Bankabrechnung (siehe StatementImport) ----
    public static final String EXTRA_PREFILL_ACTION = "prefillAction";
    public static final String EXTRA_PREFILL_DATE = "prefillDate";
    public static final String EXTRA_PREFILL_SHARES = "prefillShares";
    public static final String EXTRA_PREFILL_PRICE = "prefillPrice";
    public static final String EXTRA_PREFILL_FEE = "prefillFee";
    public static final String EXTRA_PREFILL_NET = "prefillNet";
    /** Pfad zum zwischengespeicherten Abrechnungstext; daraus lernt die App beim Speichern die Anker. */
    public static final String EXTRA_STATEMENT_TEXT = "statementText";
    public static final String EXTRA_STATEMENT_ISIN = "statementIsin";

    private static final String BUY = "buy";
    private static final String SELL = "sell";
    private static final String DIVIDEND = "dividend";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final Calendar selectedDate = Calendar.getInstance();

    private Repository repository;
    private String depot;
    private String kmyId;
    private String securityName = "";
    private double sharesHeld;
    private double taxRate;

    /** Die geladene Bewegung; {@code null} im Neu-Modus. */
    private SecurityTx loaded;
    private boolean readOnly;

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup toggleAction;
    private TextView actionHeading;
    private TextView textSecurity;
    private TextInputLayout dateLayout;
    private TextInputEditText editDate;
    private TextInputLayout grossLayout;
    private TextInputLayout feeLayout;
    private TextInputLayout netLayout;
    private TextInputLayout priceLayout;
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
    private LinearLayout detailBox;
    private CalcKeyboardView calcKeyboard;

    private final Map<Field, TextInputEditText> numberFields = new EnumMap<>(Field.class);
    /** Felder, die der Nutzer selbst gefüllt hat – nur die übrigen darf die Rechnung überschreiben. */
    private final Set<Field> userSet = EnumSet.noneOf(Field.class);
    private Field lastComputed;
    private Field justEdited;
    /** Schützt vor Rückkopplung, während die Rechnung Felder beschreibt. */
    private boolean writingBack;
    private boolean conflict;
    /** Abrechnungstext der Sitzung; gesetzt, wenn die Maske aus einem eingelesenen PDF kam. */
    private String statementTextPath;
    private String statementIsin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_security_tx);

        depot = orEmpty(getIntent().getStringExtra(EXTRA_DEPOT));
        kmyId = orEmpty(getIntent().getStringExtra(EXTRA_KMY_ID));
        securityName = orEmpty(getIntent().getStringExtra(EXTRA_NAME));
        sharesHeld = getIntent().getDoubleExtra(EXTRA_SHARES_HELD, 0);
        repository = new Repository(this);
        taxRate = new SettingsStore(this).getDividendTaxPercent() / 100.0;

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toggleAction = findViewById(R.id.toggleAction);
        actionHeading = findViewById(R.id.actionHeading);
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
        detailBox = findViewById(R.id.detailBox);
        calcKeyboard = findViewById(R.id.calcKeyboard);

        numberFields.put(Field.SHARES, findViewById(R.id.editShares));
        numberFields.put(Field.PRICE, findViewById(R.id.editPrice));
        numberFields.put(Field.GROSS, findViewById(R.id.editGross));
        numberFields.put(Field.FEE, findViewById(R.id.editFee));
        numberFields.put(Field.NET, findViewById(R.id.editNet));

        editDate.setOnClickListener(v -> showDatePicker());
        // Das Kalendersymbol liegt über dem Feld und würde den Tipper sonst schlucken.
        dateLayout.setEndIconOnClickListener(v -> showDatePicker());

        toggleAction.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                applyAction();
                loadCategoryFavorites();
                recompute(null);
            }
        });
        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        loadPickers();

        statementTextPath = getIntent().getStringExtra(EXTRA_STATEMENT_TEXT);
        statementIsin = getIntent().getStringExtra(EXTRA_STATEMENT_ISIN);

        long txId = getIntent().getLongExtra(EXTRA_TX_ID, -1);
        if (txId >= 0) {
            repository.getSecurityTx(txId, this::bind);
        } else {
            setupNewMode();
        }
    }

    // ---- Modi ----

    private void setupNewMode() {
        toolbar.setTitle(R.string.security_tx_new_title);
        toggleAction.check(R.id.btnBuy);
        selectedDate.setTime(new Date());
        updateDateField();
        applyAction();
        wireNumberFields();
        loadDefaults(currentAction());
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
        prefillNumber(Field.SHARES, in.hasExtra(EXTRA_PREFILL_SHARES)
                ? in.getDoubleExtra(EXTRA_PREFILL_SHARES, 0) : null);
        prefillNumber(Field.PRICE, in.hasExtra(EXTRA_PREFILL_PRICE)
                ? in.getDoubleExtra(EXTRA_PREFILL_PRICE, 0) : null);
        prefillMoney(Field.FEE, in.hasExtra(EXTRA_PREFILL_FEE)
                ? in.getLongExtra(EXTRA_PREFILL_FEE, 0) : null);
        prefillMoney(Field.NET, in.hasExtra(EXTRA_PREFILL_NET)
                ? in.getLongExtra(EXTRA_PREFILL_NET, 0) : null);
        recompute(null);
    }

    private void prefillNumber(Field field, Double value) {
        if (value != null) {
            numberFields.get(field).setText(field == Field.SHARES
                    ? MoneyFormat.shares(value) : MoneyFormat.decimal(value, 0, 4));
            userSet.add(field);
        }
    }

    private void prefillMoney(Field field, Long cents) {
        if (cents != null) {
            numberFields.get(field).setText(MoneyFormat.plain(cents));
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
        double count = dividend ? sharesHeld : Math.abs(tx.shares);
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
        moveTotalField(dividend);
        if (!dividend) {
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
            }
        });
        input.addTextChangedListener(new SimpleWatcher(() -> {
            if (writingBack) {
                return;
            }
            if (textOf(input).trim().isEmpty()) {
                userSet.remove(field);
            } else {
                userSet.add(field);
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
        in.taxRate = taxRate;
        in.lastComputed = lastComputed;
        in.justEdited = justEdited;
        in.shares = userSet.contains(Field.SHARES) ? number(Field.SHARES) : null;
        in.price = userSet.contains(Field.PRICE) ? number(Field.PRICE) : null;
        in.grossCents = userSet.contains(Field.GROSS) ? money(Field.GROSS) : null;
        in.feeCents = userSet.contains(Field.FEE) ? money(Field.FEE) : null;
        in.netCents = userSet.contains(Field.NET) ? money(Field.NET) : null;

        SecurityAmounts.Result r = SecurityAmounts.solve(in);
        conflict = r.conflict;
        netLayout.setError(conflict ? getString(R.string.security_tx_conflict) : null);
        if (conflict) {
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
    }

    /** Schreibt einen berechneten Wert – aber nie in ein Feld, das der Nutzer selbst gefüllt hat. */
    private void writeUnset(Field field, String text) {
        if (userSet.contains(field)) {
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

    /** Gegenkonto und Kategorien aus der jüngsten Bewegung derselben Art übernehmen. */
    private void loadDefaults(String action) {
        repository.getSecurityTxDefaults(depot, kmyId, action, last -> {
            if (last == null) {
                return;
            }
            if (textOf(editAccount).trim().isEmpty()) {
                editAccount.setText(last.moneyAccount, false);
            }
            if (textOf(editFeeCategory).trim().isEmpty()) {
                editFeeCategory.setText(last.feeCategory, false);
            }
            if (textOf(editIncomeCategory).trim().isEmpty()) {
                editIncomeCategory.setText(last.incomeCategory, false);
            }
            if (DIVIDEND.equals(action) && sharesHeld > 0 && !userSet.contains(Field.SHARES)) {
                writingBack = true;
                setNumber(Field.SHARES, sharesHeld);
                writingBack = false;
            }
        });
    }

    // ---- Speichern ----

    private void save() {
        if (conflict) {
            Toast.makeText(this, R.string.security_tx_conflict, Toast.LENGTH_LONG).show();
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
        // Eine Dividende bewegt keine Stücke – stünde die eingegebene Anzahl hier, verfälschte sie den
        // Bestand. Sie diente nur dazu, aus „je Stück" den Bruttobetrag zu rechnen.
        tx.shares = dividend ? 0 : (SELL.equals(action) ? -Math.abs(count) : Math.abs(count));
        tx.amountCents = gross;
        tx.netCents = dividend ? net : gross;
        tx.feeCents = dividend ? 0 : feeCents;
        tx.moneyAccount = account;
        tx.feeCategory = textOf(editFeeCategory).trim();
        tx.incomeCategory = dividend ? textOf(editIncomeCategory).trim() : "";

        Booking booking = buildBooking(action, account, net);
        final Long feeForLearning = fee;
        Runnable done = () -> {
            Toast.makeText(this, R.string.security_tx_saved, Toast.LENGTH_SHORT).show();
            offerToLearn(action, count, number(Field.PRICE), feeForLearning, net);
        };
        if (loaded != null) {
            repository.updateManualSecurityTx(tx, booking, done);
        } else {
            repository.saveManualSecurityTx(tx, booking, done);
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
        if (statementTextPath == null) {
            finish();
            return;
        }
        final de.spahr.ausgaben.pdf.PdfText text = readStatementText();
        if (text == null) {
            finish();
            return;
        }
        TemplateLearner.Known known = new TemplateLearner.Known();
        known.action = action;
        known.shares = shares;
        known.price = DIVIDEND.equals(action) ? null : price;   // bei Dividenden steht er in Fremdwährung
        known.feeCents = feeCents;
        known.netCents = netCents;
        known.dateMillis = selectedDate.getTimeInMillis();
        final StatementTemplate learned = TemplateLearner.learn(text, known);
        if (learned.isEmpty()) {
            finish();
            return;
        }
        new AppDialog(this)
                .setTitle(R.string.statement_learn_title)
                .setMessage(R.string.statement_learn_message)
                .setPositiveButton(R.string.statement_learn_yes, (d, w) -> {
                    StatementTemplates store = new StatementTemplates(this);
                    store.save(learned);
                    // Auch die Zuordnung merken – dann findet die nächste Abrechnung das Wertpapier
                    // selbst dann, wenn die ISIN in KMyMoney nicht gepflegt ist.
                    store.rememberSecurity(statementIsin, depot, kmyId, securityName);
                    Toast.makeText(this, R.string.statement_learned, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    /** Den zwischengespeicherten Abrechnungstext einlesen; {@code null}, wenn er nicht mehr da ist. */
    private de.spahr.ausgaben.pdf.PdfText readStatementText() {
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(
                    new java.io.File(statementTextPath).toPath());
            return de.spahr.ausgaben.pdf.PdfText.fromLines(
                    new String(raw, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
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

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            updateDateField();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateField() {
        editDate.setText(dateFormat.format(selectedDate.getTime()));
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
