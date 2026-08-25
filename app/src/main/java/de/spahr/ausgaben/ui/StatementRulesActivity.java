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
import java.util.Arrays;
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
 * <p>Neue Vorlagen entstehen hier nicht — die entstehen beim ersten Erfassen einer Abrechnung und sind
 * dann vollständig. Von Hand wird nachgebessert, nicht angefangen.</p>
 */
public class StatementRulesActivity extends LocalizedActivity {

    /** Die Felder in der Reihenfolge, in der man sie in einer Abrechnung sucht. */
    private static final Field[] ORDER = {
            Field.NET, Field.FEE, Field.GROSS, Field.SHARES, Field.PRICE, Field.DATE};

    private static final String DIVIDEND = "dividend";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    private Repository repository;
    private StatementTemplates store;
    private final List<StatementTemplate> templates = new ArrayList<>();
    private int current = -1;

    private PickerTextView editTemplate;
    private TextInputLayout templateLayout;
    private TextView textEmpty;
    private LinearLayout fieldContainer;
    private View btnTry;
    private View btnSave;
    private View btnDelete;

    /** Der Arbeitsstand je Feld; er lebt in den Eingabefeldern und wird erst beim Speichern eingesammelt. */
    private final Map<Field, FieldForm> forms = new EnumMap<>(Field.class);

    private ActivityResultLauncher<String[]> tryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_rules);
        repository = new Repository(this);
        store = new StatementTemplates(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        editTemplate = findViewById(R.id.editTemplate);
        templateLayout = findViewById(R.id.templateLayout);
        textEmpty = findViewById(R.id.textEmpty);
        fieldContainer = findViewById(R.id.fieldContainer);
        btnTry = findViewById(R.id.btnTry);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);

        btnTry.setOnClickListener(v -> tryLauncher.launch(new String[]{"application/pdf"}));
        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        tryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        runTry(uri);
                    }
                });

        load();
    }

    // ---- Vorlagen ----

    private void load() {
        templates.clear();
        templates.addAll(store.all());
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
        for (Field field : ORDER) {
            FieldForm form = new FieldForm(field, t);
            forms.put(field, form);
            fieldContainer.addView(form.view);
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

            PickerAdapters.plain(direction, Arrays.asList(
                    getString(R.string.statement_rules_same_line),
                    getString(R.string.statement_rules_line_below)));
            PickerAdapters.plain(position, stellen());

            AnchorRule rule = template.rule(field);
            if (rule != null) {
                anchors.addAll(rule.anchors);
                direction.setText(getString(rule.direction == AnchorRule.Direction.LINE_BELOW
                        ? R.string.statement_rules_line_below
                        : R.string.statement_rules_same_line), false);
                currency.setText(rule.currency);
                sum.setChecked(rule.sum);
                position.setText(stelle(rule.position, rule.nth), false);
            } else {
                direction.setText(getString(R.string.statement_rules_same_line), false);
                position.setText(stelle(AnchorRule.Position.LAST, 1), false);
            }
            view.findViewById(R.id.btnAddAnchor).setOnClickListener(v -> {
                readBack();
                anchors.add("");
                renderRows();
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
                ((TextInputEditText) row.findViewById(R.id.editAnchor)).setText(anchors.get(i));
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
            List<String> kept = new ArrayList<>();
            for (String anchor : anchors) {
                if (!anchor.trim().isEmpty()) {
                    kept.add(anchor.trim());
                }
            }
            if (kept.isEmpty()) {
                return null;
            }
            boolean below = getString(R.string.statement_rules_line_below)
                    .contentEquals(direction.getText() == null ? "" : direction.getText());
            String code = currency.getText() == null ? "" : currency.getText().toString().trim();
            String gewaehlt = position.getText() == null ? "" : position.getText().toString();
            AnchorRule.Position wo = AnchorRule.Position.LAST;
            int nth = 1;
            for (int i = 1; i <= MAX_STELLE; i++) {
                if (stelle(AnchorRule.Position.FIRST, i).equals(gewaehlt)) {
                    wo = AnchorRule.Position.FIRST;
                    nth = i;
                } else if (stelle(AnchorRule.Position.LAST, i).equals(gewaehlt)) {
                    nth = i;
                }
            }
            return new AnchorRule(kept,
                    below ? AnchorRule.Direction.LINE_BELOW : AnchorRule.Direction.SAME_LINE,
                    sum.isChecked(), code, wo, nth);
        }

        Field field() {
            return field;
        }
    }

    /** Bis zu dieser Stelle lässt sich von Hand wählen — so weit sucht auch der Lerner. */
    private static final int MAX_STELLE = 3;

    /** Die Auswahl „welche Zahl der Zeile", von aussen nach innen. */
    private List<String> stellen() {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= MAX_STELLE; i++) {
            out.add(stelle(AnchorRule.Position.LAST, i));
        }
        for (int i = 1; i <= MAX_STELLE; i++) {
            out.add(stelle(AnchorRule.Position.FIRST, i));
        }
        return out;
    }

    /** Die Beschriftung zu einer Stelle: „die letzte", „die 2. von rechts", „die erste (Spalte)" … */
    private String stelle(AnchorRule.Position wo, int nth) {
        if (nth == 1) {
            return getString(wo == AnchorRule.Position.FIRST
                    ? R.string.statement_rules_first_number : R.string.statement_rules_last_number);
        }
        return getString(wo == AnchorRule.Position.FIRST
                ? R.string.statement_rules_nth_left : R.string.statement_rules_nth_right, nth);
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
        return new StatementTemplate(templates.get(current).action, rules);
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
        store.saveAll(templates);
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
                    store.saveAll(templates);
                    load();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Probe ----

    /**
     * Wendet die <b>gerade bearbeiteten</b> Regeln auf eine echte Abrechnung an und zeigt, was dabei
     * herauskommt. Ohne das wäre das Eintippen von Beschriftungen raten; gebucht wird nichts.
     */
    private void runTry(Uri uri) {
        if (current < 0) {
            return;
        }
        final StatementTemplate built = edited();
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
            runOnUiThread(() -> showTryResult(built, text));
        });
    }

    private void showTryResult(StatementTemplate built, PdfText text) {
        StringBuilder message = new StringBuilder();
        if (built.score(text) == 0) {
            message.append(getString(R.string.statement_rules_try_nomatch)).append("\n\n");
        }
        for (Field field : ORDER) {
            AnchorRule rule = built.rule(field);
            String value = rule == null ? null : valueOf(field, rule, text);
            String anchor = rule == null ? null : rule.matchedAnchor(text);
            message.append(nameOf(field, built.action)).append(":  ");
            if (value == null) {
                message.append(getString(R.string.statement_rules_nothing));
            } else {
                message.append(value);
                if (anchor != null) {
                    message.append("   (").append(anchor).append(')');
                }
            }
            message.append('\n');
        }
        new AppDialog(this)
                .setTitle(R.string.statement_rules_try_title)
                .setMessage(message.toString().trim())
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
        if (raw == null) {
            return null;
        }
        if (field == Field.SHARES) {
            return MoneyFormat.shares(raw);
        }
        if (field == Field.PRICE) {
            return MoneyFormat.decimal(raw, 2, 4);
        }
        return MoneyFormat.plain(Math.round(raw * 100.0));
    }
}
