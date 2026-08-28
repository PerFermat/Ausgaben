package de.spahr.ausgaben.ui;

import android.net.Uri;
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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.pdf.PdfTextExtractor;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;
import de.spahr.ausgaben.statement.StatementTemplate.Field;

/**
 * Die gelernten Erkennungsregeln von Hand nachbessern.
 *
 * <p>Der Lerner trifft das Meiste, aber er kennt je Feld nur <b>eine</b> Beschriftung — und die reicht
 * nicht, wo eine Bank Zeilen weglässt oder wechselt. Hier lassen sich deshalb vor allem Rückfälle
 * angeben: {@code Valuta}, ersatzweise {@code Zahltag}, ersatzweise {@code Ex-Tag}. Gesucht wird in dieser
 * Reihenfolge, es gilt die erste Beschriftung, die einen Wert trägt.</p>
 *
 * <p>Dieselbe Kette löst den Währungsfall: das Brutto steht bei einem dollarnotierten Papier in der
 * Umrechnungszeile und bei einem euronotierten in der Bruttozeile.</p>
 *
 * <p>Gearbeitet wird an einer <b>Testabrechnung</b>: eine einmal gewählte PDF-Datei hängt an der Seite,
 * stellt die zu ihr passende Vorlage ein und lässt jeden Bereich unter seiner Überschrift zeigen, was
 * seine Regel darin liest. Die Beschriftungsfelder legen dann die im Dokument gefundenen Werte zur
 * Auswahl vor, statt sie erraten zu lassen — buchstabengenau so, wie die App sie sieht.</p>
 *
 * <p>Neue Vorlagen entstehen hier nicht — die entstehen beim ersten Erfassen einer Abrechnung und sind
 * dann vollständig. Von Hand wird nachgebessert, nicht angefangen.</p>
 */
public class StatementRulesActivity extends LocalizedActivity {

    public static final String EXTRA_DEPOT = "depot";

    private static final String DIVIDEND = "dividend";

    /**
     * Die Felder dieser Art in der Reihenfolge, in der sie auf der Seite stehen.
     *
     * <p>Das Datum ganz oben, weil es das einzige Feld ist, das jede Abrechnung trägt — die Geldfelder
     * folgen von der Endsumme abwärts. Bei einer Dividende fehlen Anzahl und Kurs: eine
     * Ertragsgutschrift bucht keine Stücke, und ein Kurs je Stück ist dort die Dividende selbst, die
     * schon als Brutto dasteht.</p>
     */
    private static Field[] fieldsFor(String action) {
        if (DIVIDEND.equals(action)) {
            return new Field[]{Field.DATE, Field.NET, Field.FEE, Field.GROSS};
        }
        return new Field[]{Field.DATE, Field.NET, Field.FEE, Field.GROSS, Field.SHARES, Field.PRICE};
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    private Repository repository;
    private StatementTemplates store;
    private String depot;
    private final List<StatementTemplate> templates = new ArrayList<>();
    private int current = -1;

    private PickerTextView editTemplate;
    private TextInputLayout templateLayout;
    private TextView textEmpty;
    private LinearLayout fieldContainer;
    private View btnTry;
    private View btnSave;
    private View btnDelete;
    private View testButtons;
    private TextView textTestFile;

    /**
     * Die Testabrechnung, an der die Regeln gemessen werden — sie hält die ganze Sitzung über und wird
     * nicht gespeichert.
     *
     * <p>Sie ist der Unterschied zwischen Raten und Sehen: ohne sie tippt man Beschriftungen ein und
     * erfährt erst beim nächsten Import, ob sie greifen.</p>
     */
    private Uri testUri;
    private PdfText testText;
    /** Die eigene Rechentastatur; sie bedient das Feld für die feste Gebühr. */
    private CalcKeyboardView calcKeyboard;

    /** Der Arbeitsstand je Feld; er lebt in den Eingabefeldern und wird erst beim Speichern eingesammelt. */
    private final Map<Field, FieldForm> forms = new EnumMap<>(Field.class);

    /**
     * Die Kategorienliste für die feste Gebühr; {@code null}, solange sie noch lädt.
     *
     * <p>Sie trifft asynchron ein, die Feldformulare entstehen aber sofort. Deshalb wird sie hier
     * gehalten und beim Eintreffen nachgereicht — sonst hinge es am Zufall, was zuerst da ist.</p>
     */
    private CategoryFilterAdapter categoryAdapter;

    private ActivityResultLauncher<String[]> tryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_rules);
        repository = new Repository(this);
        store = new StatementTemplates(this);
        depot = getIntent().getStringExtra(EXTRA_DEPOT);
        if (depot == null) {
            depot = "";
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        editTemplate = findViewById(R.id.editTemplate);
        templateLayout = findViewById(R.id.templateLayout);
        textEmpty = findViewById(R.id.textEmpty);
        fieldContainer = findViewById(R.id.fieldContainer);
        btnTry = findViewById(R.id.btnTry);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        testButtons = findViewById(R.id.testButtons);
        textTestFile = findViewById(R.id.textTestFile);
        calcKeyboard = findViewById(R.id.calcKeyboard);
        calcKeyboard.reserveSpaceWith(findViewById(R.id.calcSpacer));

        btnTry.setOnClickListener(v -> tryLauncher.launch(new String[]{"application/pdf"}));
        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnShowPdf).setOnClickListener(v -> showPdf());
        findViewById(R.id.btnShowText).setOnClickListener(v -> {
            if (testText != null) {
                showText(testText);
            }
        });

        tryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        useTestStatement(uri);
                    }
                });

        repository.getCategoriesGrouped(g -> {
            categoryAdapter = new CategoryFilterAdapter(this, null,
                    getString(R.string.category_group_expense), g.expense,
                    getString(R.string.category_group_income), g.income);
            FieldForm fee = forms.get(Field.FEE);
            if (fee != null) {
                fee.attachCategories(categoryAdapter);
            }
        });

        load();
    }

    // ---- Vorlagen ----

    private void load() {
        templates.clear();
        templates.addAll(store.all(depot));
        boolean any = !templates.isEmpty();
        textEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        templateLayout.setVisibility(any ? View.VISIBLE : View.GONE);
        btnTry.setVisibility(any ? View.VISIBLE : View.GONE);
        btnSave.setVisibility(any ? View.VISIBLE : View.GONE);
        btnDelete.setVisibility(any ? View.VISIBLE : View.GONE);
        fieldContainer.removeAllViews();
        forms.clear();
        if (!any) {
            current = -1;
            return;
        }
        List<String> labels = new ArrayList<>();
        for (StatementTemplate t : templates) {
            labels.add(labelOf(t));
        }
        PickerAdapters.plain(editTemplate, labels);
        editTemplate.setOnItemClickListener((parent, view, position, id) -> show(position));
        show(0);
    }

    /** „Kauf — Endbetrag zu Ihren Lasten": Art und Gesamtsumme unterscheiden die Vorlagen einer Bank. */
    private String labelOf(StatementTemplate t) {
        AnchorRule net = t.rule(Field.NET);
        String anchor = net == null || net.anchors.isEmpty() ? "" : net.anchors.get(0);
        String action = actionLabel(t.action);
        return anchor.isEmpty() ? action : action + "  —  " + anchor;
    }

    private String actionLabel(String action) {
        if ("sell".equals(action)) {
            return getString(R.string.action_sell);
        }
        return DIVIDEND.equals(action) ? getString(R.string.action_dividend)
                : getString(R.string.action_buy);
    }

    private void show(int index) {
        if (index < 0 || index >= templates.size()) {
            return;
        }
        current = index;
        StatementTemplate t = templates.get(index);
        editTemplate.setText(labelOf(t), false);
        fieldContainer.removeAllViews();
        forms.clear();
        for (Field field : fieldsFor(t.action)) {
            FieldForm form = new FieldForm(field, t);
            forms.put(field, form);
            fieldContainer.addView(form.view);
        }
        updateAllFound();
    }

    /** Nach jedem Wechsel der Vorlage oder der Testabrechnung: alle Bereiche neu ausrechnen. */
    private void updateAllFound() {
        for (FieldForm form : forms.values()) {
            form.updateFound();
        }
    }

    // ---- Ein Feld ----

    private final class FieldForm {

        private final Field field;
        private final View view;
        private final LinearLayout container;
        private final PickerTextView direction;
        private final PickerTextView position;
        private final TextInputEditText currency;
        private final MaterialSwitch sum;
        /** Nur bei der Gebühr sichtbar: ein Betrag, den die Bank nicht ausdruckt. */
        private final TextInputEditText fixedFee;
        private final PickerTextView fixedFeeCategory;
        /** Nur beim Gesamtbetrag sichtbar: steckt die feste Gebühr schon darin? */
        private final MaterialSwitch fixedFeeInTotal;
        /** Was die Regel dieses Bereichs in der Testabrechnung liest. */
        private final TextView found;
        private final List<String> anchors = new ArrayList<>();

        FieldForm(Field field, StatementTemplate template) {
            this.field = field;
            LayoutInflater inflater = LayoutInflater.from(StatementRulesActivity.this);
            view = inflater.inflate(R.layout.item_statement_field, fieldContainer, false);
            ((TextView) view.findViewById(R.id.textFieldName)).setText(nameOf(field, template.action));
            container = view.findViewById(R.id.anchorContainer);
            direction = view.findViewById(R.id.editDirection);
            position = view.findViewById(R.id.editPosition);
            currency = view.findViewById(R.id.editCurrency);
            sum = view.findViewById(R.id.switchSum);
            fixedFee = view.findViewById(R.id.editFixedFee);
            fixedFeeCategory = view.findViewById(R.id.editFixedFeeCategory);
            fixedFeeInTotal = view.findViewById(R.id.switchFixedFeeInTotal);
            found = view.findViewById(R.id.textFieldFound);
            // Der feste Betrag gehört zur Gebühr, die Frage nach dem Gesamtbetrag zu diesem – jede an
            // ihren Platz, statt beide in einen Kasten für sich.
            view.findViewById(R.id.fixedFeeBox)
                    .setVisibility(field == Field.FEE ? View.VISIBLE : View.GONE);
            fixedFeeInTotal.setVisibility(field == Field.NET ? View.VISIBLE : View.GONE);
            if (field == Field.FEE) {
                wireCalcField(fixedFee);
                if (template.fixedFeeCents > 0) {
                    fixedFee.setText(MoneyFormat.plain(template.fixedFeeCents));
                }
                fixedFeeCategory.setText(template.fixedFeeCategory, false);
                attachCategories(categoryAdapter);
                // Der Schalter beim Gesamtbetrag hat erst einen Gegenstand, wenn hier etwas steht.
                fixedFee.addTextChangedListener(new SimpleWatcher(
                        StatementRulesActivity.this::updateFixedFeeSwitch));
            }
            if (field == Field.NET) {
                fixedFeeInTotal.setChecked(template.fixedFeeInTotal);
                // Ohne festen Betrag hat die Frage keinen Gegenstand.
                dim(fixedFeeInTotal, template.fixedFeeCents > 0);
            }

            PickerAdapters.plain(direction, richtungen());

            AnchorRule rule = template.rule(field);
            if (rule != null) {
                anchors.addAll(rule.anchors);
                direction.setText(richtung(rule.direction, rule.lineDistance), false);
                currency.setText(rule.currency);
                sum.setChecked(rule.sum);
                position.setText(stelle(rule.position, rule.nth), false);
            } else {
                direction.setText(getString(R.string.statement_rules_same_line), false);
                position.setText(stelle(AnchorRule.Position.LAST, 1), false);
            }
            updateStellen();
            view.findViewById(R.id.btnAddAnchor).setOnClickListener(v -> {
                readBack();
                anchors.add("");
                renderRows();
            });
            // Jede Einstellung, die das Lesen ändert, rechnet die Fundstelle sofort neu: sonst müsste
            // man raten, ob eine Änderung etwas gebracht hat.
            direction.setOnItemClickListener((p, v, at, id) -> {
                updateStellen();   // «in der Spalte» gibt es nur, wenn der Wert nicht hier steht
                updateFound();
            });
            position.setOnItemClickListener((p, v, at, id) -> updateFound());
            currency.addTextChangedListener(new SimpleWatcher(this::updateFound));
            sum.setOnCheckedChangeListener((b, checked) -> {
                readBack();     // sonst gingen gerade getippte Beschriftungen beim Neuaufbau verloren
                renderRows();
                updateFound();
            });
            renderRows();
        }

        /**
         * Die Beschriftungen als Zeilen in ihrer Rangfolge. Neu gebaut wird bei jeder Verschiebung — das
         * ist bei einer Handvoll Zeilen billiger als der Versuch, Ansichten zu tauschen.
         */
        private void renderRows() {
            container.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(StatementRulesActivity.this);
            for (int i = 0; i < anchors.size(); i++) {
                final int at = i;
                View row = inflater.inflate(R.layout.item_statement_anchor, container, false);
                TextInputEditText edit = row.findViewById(R.id.editAnchor);
                edit.setText(anchors.get(i));
                edit.addTextChangedListener(new SimpleWatcher(this::updateFound));
                if (testText != null) {
                    // Mit einer Testabrechnung ist das Feld zuerst eine Auswahl und erst auf Wunsch ein
                    // Eingabefeld: die Beschriftung muss buchstabengenau so dastehen, wie die App sie
                    // sieht, und das trifft man tippend selten auf Anhieb.
                    edit.setShowSoftInputOnFocus(false);
                    edit.setOnClickListener(v -> pickAnchor(edit));
                }
                ImageButton up = row.findViewById(R.id.btnAnchorUp);
                ImageButton down = row.findViewById(R.id.btnAnchorDown);
                // An den Enden führt der Pfeil nirgendwohin; abgeblendet sagt das, ohne ihn zu verstecken.
                dim(up, at > 0);
                dim(down, at < anchors.size() - 1);
                up.setOnClickListener(v -> swap(at, at - 1));
                down.setOnClickListener(v -> swap(at, at + 1));
                row.findViewById(R.id.btnAnchorRemove).setOnClickListener(v -> {
                    readBack();
                    anchors.remove(at);
                    renderRows();
                });
                container.addView(row);
            }
            // Der Hinweis auf die Rangfolge lohnt erst, wenn es eine gibt.
            view.findViewById(R.id.textFieldHint)
                    .setVisibility(anchors.size() > 1 && !sum.isChecked() ? View.VISIBLE : View.GONE);
        }

        private void swap(int from, int to) {
            readBack();
            if (from < 0 || to < 0 || from >= anchors.size() || to >= anchors.size()) {
                return;
            }
            String moved = anchors.remove(from);
            anchors.add(to, moved);
            renderRows();
        }

        /** Was in den Feldern steht, zurück ins Modell — vor jedem Verschieben und vor dem Speichern. */
        private void readBack() {
            for (int i = 0; i < container.getChildCount() && i < anchors.size(); i++) {
                TextInputEditText edit = container.getChildAt(i).findViewById(R.id.editAnchor);
                anchors.set(i, edit.getText() == null ? "" : edit.getText().toString().trim());
            }
        }

        /** Die Regel, wie sie jetzt dasteht; {@code null}, wenn keine Beschriftung übrig blieb. */
        AnchorRule toRule() {
            readBack();
            List<String> kept = keptAnchors();
            return kept.isEmpty() ? null : ruleFor(kept, sum.isChecked());
        }

        /** Die eingetragenen Beschriftungen ohne die leeren Zeilen. */
        private List<String> keptAnchors() {
            List<String> kept = new ArrayList<>();
            for (String anchor : anchors) {
                if (!anchor.trim().isEmpty()) {
                    kept.add(anchor.trim());
                }
            }
            return kept;
        }

        /**
         * Setzt die Auswahl „welche Zahl" passend zur Richtung.
         *
         * <p>Bei „in derselben Zeile" fehlt die Spaltenwahl — sie fände dort nie etwas. Stand sie
         * vorher, fällt die Einstellung auf den Regelfall zurück, statt stumm eine Regel zu
         * hinterlassen, die nichts liest.</p>
         */
        private void updateStellen() {
            boolean mitSpalte = chosenDirection() != AnchorRule.Direction.SAME_LINE;
            if (!mitSpalte && chosenPosition() == AnchorRule.Position.COLUMN) {
                position.setText(stelle(AnchorRule.Position.LAST, 1), false);
            }
            PickerAdapters.plain(position, stellen(mitSpalte));
        }

        /**
         * Eine Regel aus den gerade eingestellten Angaben. Der Anker kommt von aussen, damit sich
         * damit auch ein einzelner prüfen lässt — für die Anzeige der Einzelwerte beim Summieren und
         * für die Auswahlliste, die ja nur zeigen darf, was die fertige Regel auch findet.
         */
        private AnchorRule ruleFor(List<String> anchorList, boolean summed) {
            return new AnchorRule(anchorList, chosenDirection(), summed, chosenCurrency(),
                    chosenPosition(), chosenNth(), chosenLineDistance());
        }

        private AnchorRule.Direction chosenDirection() {
            String gewaehlt = direction.getText() == null ? "" : direction.getText().toString();
            if (getString(R.string.statement_rules_same_line).equals(gewaehlt)) {
                return AnchorRule.Direction.SAME_LINE;
            }
            for (int i = 0; i <= AnchorRule.MAX_DISTANCE; i++) {
                if (richtung(AnchorRule.Direction.LINE_ABOVE, i).equals(gewaehlt)) {
                    return AnchorRule.Direction.LINE_ABOVE;
                }
            }
            return AnchorRule.Direction.LINE_BELOW;
        }

        /** 0 heisst «suchend» – siehe {@link AnchorRule#lineDistance}. */
        private int chosenLineDistance() {
            String gewaehlt = direction.getText() == null ? "" : direction.getText().toString();
            for (int i = 1; i <= AnchorRule.MAX_DISTANCE; i++) {
                if (richtung(AnchorRule.Direction.LINE_BELOW, i).equals(gewaehlt)
                        || richtung(AnchorRule.Direction.LINE_ABOVE, i).equals(gewaehlt)) {
                    return i;
                }
            }
            return 0;
        }

        private AnchorRule.Position chosenPosition() {
            String gewaehlt = position.getText() == null ? "" : position.getText().toString();
            if (stelle(AnchorRule.Position.COLUMN, 1).equals(gewaehlt)) {
                return AnchorRule.Position.COLUMN;
            }
            for (int i = 1; i <= MAX_STELLE; i++) {
                if (stelle(AnchorRule.Position.FIRST, i).equals(gewaehlt)) {
                    return AnchorRule.Position.FIRST;
                }
            }
            return AnchorRule.Position.LAST;
        }

        private int chosenNth() {
            String gewaehlt = position.getText() == null ? "" : position.getText().toString();
            for (int i = 1; i <= MAX_STELLE; i++) {
                if (stelle(AnchorRule.Position.FIRST, i).equals(gewaehlt)
                        || stelle(AnchorRule.Position.LAST, i).equals(gewaehlt)) {
                    return i;
                }
            }
            return 1;
        }

        private String chosenCurrency() {
            return currency.getText() == null ? "" : currency.getText().toString().trim();
        }

        /**
         * Schreibt unter die Überschrift, was die Regel in der Testabrechnung liest.
         *
         * <p>Beim Summieren stehen die Einzelwerte da und dahinter ihre Summe — sonst sähe man einer
         * zu hohen Steuer nicht an, welche Zeile zu viel mitgezählt hat.</p>
         */
        void updateFound() {
            if (testText == null) {
                found.setVisibility(View.GONE);
                return;
            }
            found.setVisibility(View.VISIBLE);
            readBack();
            List<String> kept = keptAnchors();
            AnchorRule rule = kept.isEmpty() ? null : ruleFor(kept, sum.isChecked());
            String value = rule == null ? null : valueOf(field, rule, testText);
            if (value == null) {
                found.setText(getString(R.string.statement_rules_found,
                        getString(R.string.statement_rules_nothing)));
                return;
            }
            StringBuilder was = new StringBuilder();
            if (sum.isChecked() && kept.size() > 1) {
                for (String anchor : kept) {
                    String part = valueOf(field,
                            ruleFor(java.util.Collections.singletonList(anchor), false), testText);
                    if (part != null) {
                        was.append(was.length() > 0 ? " + " : "").append(anchor).append(' ').append(part);
                    }
                }
                was.append(was.length() > 0 ? "  =  " : "").append(value);
            } else {
                was.append(value);
                String anchor = rule.matchedAnchor(testText);
                if (anchor != null && kept.size() > 1) {
                    was.append("   (").append(anchor).append(')');
                }
            }
            found.setText(getString(R.string.statement_rules_found, was.toString()));
        }

        /**
         * Legt die Werte der Testabrechnung zur Auswahl vor — beim Datum die gefundenen Datumsangaben,
         * sonst die Zahlen, jeweils mit ihrer Beschriftung.
         *
         * <p>Gelesen wird mit den Einstellungen, die im Bereich gerade stehen: wer „eine Zeile tiefer"
         * und „die 2. von rechts" gewählt hat, bekommt die Zahlen zu sehen, die <b>so</b> erreichbar
         * sind. Alles andere wäre eine Liste voller Werte, die die fertige Regel nie fände.</p>
         */
        private void pickAnchor(TextInputEditText edit) {
            readBack();
            List<String> labels = new ArrayList<>();
            List<CharSequence> shown = new ArrayList<>();
            if (field == Field.DATE) {
                for (de.spahr.ausgaben.statement.StatementScan.DateCandidate c
                        : de.spahr.ausgaben.statement.StatementScan.dates(testText)) {
                    labels.add(c.label);
                    shown.add(c.label + ":  " + dateFormat.format(new Date(c.millis)));
                }
            } else {
                for (de.spahr.ausgaben.statement.StatementScan.ValueCandidate c
                        : de.spahr.ausgaben.statement.StatementScan.values(testText, chosenDirection(),
                        chosenLineDistance(), chosenPosition(), chosenNth(), chosenCurrency())) {
                    labels.add(c.label);
                    shown.add(c.label + ":  " + formatValue(field, c.value));
                }
            }
            shown.add(getString(R.string.statement_rules_type_own));
            new AppDialog(StatementRulesActivity.this)
                    .setTitle(R.string.statement_rules_pick_value)
                    .setItems(shown.toArray(new CharSequence[0]), (d, which) -> {
                        if (which >= labels.size()) {
                            // Von Hand: ab jetzt ist das Feld ein gewöhnliches Eingabefeld.
                            edit.setOnClickListener(null);
                            edit.setShowSoftInputOnFocus(true);
                            edit.requestFocus();
                            return;
                        }
                        edit.setText(labels.get(which));
                        readBack();
                        updateFound();
                    })
                    .show();
        }

        /** Die Kategorienliste nachreichen – sie trifft später ein als das Formular. */
        void attachCategories(CategoryFilterAdapter adapter) {
            if (adapter != null && field == Field.FEE) {
                String stand = fixedFeeCategory.getText() == null
                        ? "" : fixedFeeCategory.getText().toString();
                PickerAdapters.categories(fixedFeeCategory, adapter);
                // Das Anhängen des Adapters leert das Feld – der Stand von vorhin gehört zurück.
                fixedFeeCategory.setText(stand, false);
            }
        }

        /** Der feste Betrag in Cent; 0, wenn keiner eingetragen ist. */
        long fixedFeeCents() {
            if (field != Field.FEE) {
                return 0;
            }
            String raw = fixedFee.getText() == null ? "" : fixedFee.getText().toString().trim();
            if (raw.isEmpty()) {
                return 0;
            }
            Long cents = de.spahr.ausgaben.settings.AmountExpression.toCents(raw);
            return cents == null ? 0 : Math.abs(cents);
        }

        String fixedFeeCategory() {
            if (field != Field.FEE || fixedFeeCategory.getText() == null) {
                return "";
            }
            return fixedFeeCategory.getText().toString().trim();
        }

        boolean fixedFeeInTotal() {
            return field == Field.NET && fixedFeeInTotal.isChecked();
        }

        MaterialSwitch fixedFeeInTotalSwitch() {
            return fixedFeeInTotal;
        }

        Field field() {
            return field;
        }
    }

    /** Bis zu dieser Stelle lässt sich von Hand wählen — so weit sucht auch der Lerner. */
    private static final int MAX_STELLE = 6;

    /**
     * Die Auswahl „wo steht der Wert": in derselben Zeile, irgendwo darunter, oder genau eine, zwei,
     * drei Zeilen tiefer.
     *
     * <p>Die suchende Fassung steht vorn, weil sie in den meisten Belegen das Richtige tut. Die festen
     * Abstände sind für die Fälle, in denen sie an einer Zwischenzeile hängenbleibt, die zufällig eine
     * Zahl trägt.</p>
     */
    private List<String> richtungen() {
        List<String> out = new ArrayList<>();
        out.add(getString(R.string.statement_rules_same_line));
        for (int i = 0; i <= AnchorRule.MAX_DISTANCE; i++) {
            out.add(richtung(AnchorRule.Direction.LINE_BELOW, i));
            out.add(richtung(AnchorRule.Direction.LINE_ABOVE, i));
        }
        return out;
    }

    /** Die Beschriftung zu einer Richtung samt Abstand. */
    private String richtung(AnchorRule.Direction dir, int lineDistance) {
        if (dir == AnchorRule.Direction.SAME_LINE) {
            return getString(R.string.statement_rules_same_line);
        }
        boolean above = dir == AnchorRule.Direction.LINE_ABOVE;
        if (lineDistance <= 0) {
            return getString(above ? R.string.statement_rules_line_above
                    : R.string.statement_rules_line_below);
        }
        if (lineDistance == 1) {
            return getString(above ? R.string.statement_rules_above_one
                    : R.string.statement_rules_below_one);
        }
        return getString(above ? R.string.statement_rules_above_n : R.string.statement_rules_below_n,
                lineDistance);
    }

    /**
     * Die Auswahl „welche Zahl der Zeile", von aussen nach innen — und zuletzt die Spalte.
     *
     * <p>Die Spaltenwahl steht nur zur Verfügung, wenn der Wert nicht in derselben Zeile steht: dort
     * wäre die Spalte der Beschriftung die Beschriftung selbst, die Einstellung fände also nie
     * etwas.</p>
     */
    private List<String> stellen(boolean mitSpalte) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= MAX_STELLE; i++) {
            out.add(stelle(AnchorRule.Position.LAST, i));
        }
        for (int i = 1; i <= MAX_STELLE; i++) {
            out.add(stelle(AnchorRule.Position.FIRST, i));
        }
        if (mitSpalte) {
            out.add(stelle(AnchorRule.Position.COLUMN, 1));
        }
        return out;
    }

    /** Die Beschriftung zu einer Stelle: „die letzte", „die 2. von rechts", „die erste (Spalte)" … */
    private String stelle(AnchorRule.Position wo, int nth) {
        if (wo == AnchorRule.Position.COLUMN) {
            return getString(R.string.statement_rules_column);
        }
        if (nth == 1) {
            return getString(wo == AnchorRule.Position.FIRST
                    ? R.string.statement_rules_first_number : R.string.statement_rules_last_number);
        }
        return getString(wo == AnchorRule.Position.FIRST
                ? R.string.statement_rules_nth_left : R.string.statement_rules_nth_right, nth);
    }

    /**
     * Hängt ein Betragsfeld an die eigene Rechentastatur — dieselbe wie in der Erfassungsmaske.
     *
     * <p>Die System-Tastatur taugt hier nicht: ihr Ziffernblock kennt nur den Punkt, und wer das Komma
     * als Dezimalzeichen eingestellt hat, könnte gar keines eingeben. Nebenbei kommt damit das Rechnen
     * mit: eine Ordergebühr steht auch mal als {@code 0,99*2} da.</p>
     */
    private void wireCalcField(TextInputEditText input) {
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
    }

    /**
     * Blendet den Schalter «Feste Gebühr steckt schon im Gesamtbetrag» ab, solange keine feste Gebühr
     * eingetragen ist. Beide Formulare stehen gleichzeitig auf der Seite, deshalb geht das sofort — ohne
     * das müsste man die Seite verlassen und wieder aufsuchen, damit der Schalter erwacht.
     */
    private void updateFixedFeeSwitch() {
        FieldForm fee = forms.get(Field.FEE);
        FieldForm net = forms.get(Field.NET);
        if (fee != null && net != null) {
            dim(net.fixedFeeInTotalSwitch(), fee.fixedFeeCents() > 0);
        }
    }

    private static void dim(View button, boolean usable) {
        button.setEnabled(usable);
        button.setAlpha(usable ? 1f : 0.3f);
    }

    /** Die Feldnamen sind dieselben wie in der Erfassungsmaske – sie richten sich nach der Art. */
    private String nameOf(Field field, String action) {
        boolean dividend = DIVIDEND.equals(action);
        switch (field) {
            case NET:
                return getString(dividend ? R.string.security_tx_net : R.string.security_tx_total);
            case FEE:
                return getString(dividend ? R.string.security_tx_tax : R.string.security_tx_fee);
            case GROSS:
                return getString(R.string.security_tx_gross);
            case SHARES:
                return getString(R.string.security_tx_shares);
            case PRICE:
                return getString(dividend
                        ? R.string.security_tx_price_dividend : R.string.security_tx_price);
            default:
                return getString(R.string.date_hint);
        }
    }

    // ---- Speichern und Löschen ----

    /** Die Vorlage, wie sie gerade in den Feldern steht. */
    private StatementTemplate edited() {
        Map<Field, AnchorRule> rules = new EnumMap<>(Field.class);
        for (FieldForm form : forms.values()) {
            AnchorRule rule = form.toRule();
            if (rule != null) {
                rules.put(form.field(), rule);
            }
        }
        long fixedFee = 0;
        String fixedCategory = "";
        boolean inTotal = false;
        for (FieldForm form : forms.values()) {
            if (form.field() == Field.FEE) {
                fixedFee = form.fixedFeeCents();
                fixedCategory = form.fixedFeeCategory();
            } else if (form.field() == Field.NET) {
                inTotal = form.fixedFeeInTotal();
            }
        }
        return new StatementTemplate(templates.get(current).action, rules,
                fixedFee, fixedCategory, inTotal);
    }

    private void save() {
        if (current < 0) {
            return;
        }
        StatementTemplate built = edited();
        if (built.rule(Field.NET) == null) {
            // Ohne die Gesamtsumme erkennt sich die Vorlage nicht wieder und könnte auch nichts buchen.
            Toast.makeText(this, R.string.security_tx_need_amounts, Toast.LENGTH_LONG).show();
            return;
        }
        templates.set(current, built);
        store.saveAll(depot, templates);
        Toast.makeText(this, R.string.statement_rules_saved, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete() {
        if (current < 0) {
            return;
        }
        AppDialog.destructive(this)
                .setTitle(R.string.statement_rules_delete_title)
                .setMessage(R.string.statement_rules_delete_message)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    templates.remove(current);
                    store.saveAll(depot, templates);
                    load();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Probe ----

    /**
     * Nimmt eine echte Abrechnung als Messlatte an: ab dann zeigt jeder Bereich, was seine Regel darin
     * liest, und die Beschriftungsfelder legen die gefundenen Werte zur Auswahl vor.
     */
    private void useTestStatement(Uri uri) {
        Toast.makeText(this, R.string.statement_reading, Toast.LENGTH_SHORT).show();
        repository.executor().execute(() -> {
            final PdfText text;
            try {
                text = PdfTextExtractor.read(this, uri);
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.statement_unreadable, Toast.LENGTH_LONG).show());
                return;
            }
            if (!text.hasText()) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.statement_no_text, Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> {
                testUri = uri;
                testText = text;
                testButtons.setVisibility(View.VISIBLE);
                textTestFile.setVisibility(View.VISIBLE);
                textTestFile.setText(getString(R.string.statement_rules_test_file, fileName(uri)));
                switchToMatching();
            });
        });
    }

    /**
     * Stellt die Vorlage ein, die zur Testabrechnung passt.
     *
     * <p>Ohne das müsste man selbst wissen, ob die Abrechnung ein Kauf, ein Verkauf oder eine
     * Ertragsgutschrift ist — und säße sonst still vor einer Vorlage, die nichts findet, weil sie gar
     * nicht gemeint war. Passt keine, bleibt die offene stehen: die Werte darunter sind trotzdem zu
     * gebrauchen, denn genau daran bessert man die Regeln nach.</p>
     */
    private void switchToMatching() {
        StatementTemplate passend = StatementTemplates.best(templates, testText);
        int index = passend == null ? -1 : templates.indexOf(passend);
        if (index < 0) {
            Toast.makeText(this, R.string.statement_rules_try_nomatch, Toast.LENGTH_LONG).show();
            updateAllFound();
            return;
        }
        if (index == current) {
            updateAllFound();
            return;
        }
        if (current >= 0 && !edited().sameAs(templates.get(current))) {
            AppDialog.destructive(this)
                    .setTitle(R.string.statement_rules_discard_title)
                    .setMessage(R.string.statement_rules_discard_message)
                    .setPositiveButton(R.string.statement_rules_discard_title, (d, w) -> show(index))
                    .setNegativeButton(R.string.cancel, (d, w) -> updateAllFound())
                    .show();
            return;
        }
        show(index);
    }

    /** Die Testabrechnung im PDF-Betrachter des Geräts – man liest daneben, was man hier einträgt. */
    private void showPdf() {
        if (testUri == null) {
            return;
        }
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(testUri, "application/pdf")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.receipt_pdf_no_viewer, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.receipt_error, Toast.LENGTH_SHORT).show();
        }
    }

    /** Der Anzeigename der gewählten Datei; im Zweifel das letzte Stück des Uri. */
    private String fileName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            int at = c == null ? -1
                    : c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (c != null && at >= 0 && c.moveToFirst()) {
                return c.getString(at);
            }
        } catch (Exception ignored) {
            // Der Name ist nur eine Erinnerungshilfe – dafür bricht nichts ab.
        }
        return uri.getLastPathSegment() == null ? uri.toString() : uri.getLastPathSegment();
    }

    /**
     * Der geparste Text der Abrechnung, Zeile für Zeile numeriert.
     *
     * <p>Die Hilfe beim Eintippen einer Regel: eine Beschriftung muss so dastehen, wie die App sie sieht,
     * und ob der Wert eine oder zwei Zeilen tiefer steht, ist hier abzuzählen. Ohne diese Ansicht rät
     * man — die Zeilen der App sind nicht die Zeilen, die man im PDF-Betrachter sieht: sie entstehen aus
     * den Wortpositionen, und eine optisch leere Zeile gibt es hier gar nicht.</p>
     */
    private void showText(PdfText text) {
        StringBuilder all = new StringBuilder();
        int i = 0;
        for (PdfText.Line line : text.lines()) {
            all.append(String.format(Locale.GERMANY, "%2d", i++))
                    .append("  ").append(line.text()).append('\n');
        }
        new AppDialog(this)
                .setTitle(R.string.statement_rules_show_text)
                .setMessage(all.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Der gelesene Wert im Format des jeweiligen Feldes; {@code null}, wenn die Regel nichts findet. */
    private String valueOf(Field field, AnchorRule rule, PdfText text) {
        if (field == Field.DATE) {
            long millis = rule.readDate(text);
            return millis > 0 ? dateFormat.format(new Date(millis)) : null;
        }
        Double raw = rule.read(text);
        return raw == null ? null : formatValue(field, raw);
    }

    /** Eine gelesene Zahl so geschrieben, wie das Feld sie später zeigt. */
    private String formatValue(Field field, double raw) {
        if (field == Field.SHARES) {
            return MoneyFormat.shares(raw);
        }
        if (field == Field.PRICE) {
            return MoneyFormat.decimal(raw, 2, 4);
        }
        return MoneyFormat.plain(Math.round(raw * 100.0));
    }
}
