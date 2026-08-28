package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Was die Regelseite zur Auswahl stellt — und was nicht.
 *
 * <p>Die Spaltenwahl gibt es nur, wenn der Wert nicht in derselben Zeile steht: dort wäre die Spalte der
 * Beschriftung die Beschriftung selbst, die Einstellung fände also nie etwas. Ein Auswahleintrag, der
 * nichts tun kann, ist schlimmer als keiner — er sieht aus wie eine Lösung.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementRulesChoicesTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /** Legt eine Vorlage an, deren Netto-Regel in dieser Richtung liest, und öffnet die Regelseite. */
    private StatementRulesActivity seiteMit(AnchorRule.Direction direction) {
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, new AnchorRule(
                Collections.singletonList("Gesamtbetrag"), direction, false, "",
                AnchorRule.Position.LAST, 1, direction == AnchorRule.Direction.SAME_LINE ? 0 : 1));
        store.saveAll(Collections.singletonList(new StatementTemplate("buy", rules)));

        Intent intent = new Intent(ctx, StatementRulesActivity.class);
        return Robolectric.buildActivity(StatementRulesActivity.class, intent).setup().get();
    }

    /**
     * Die Einträge, die das Stellenfeld des Betrags anbietet. Der Betrag ist das zweite Feld der Seite —
     * das erste ist das Datum, für das diese Vorlage keine Regel trägt.
     */
    private List<String> stellen(StatementRulesActivity activity) {
        ViewGroup container = activity.findViewById(R.id.fieldContainer);
        View feld = container.getChildAt(1).findViewById(R.id.editPosition);
        Adapter adapter = ((android.widget.AutoCompleteTextView) feld).getAdapter();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            out.add(String.valueOf(adapter.getItem(i)));
        }
        return out;
    }

    private String spalte() {
        return ctx.getString(R.string.statement_rules_column);
    }

    @Test
    public void inDerselbenZeileGibtEsDieSpaltenwahlNicht() {
        assertFalse(stellen(seiteMit(AnchorRule.Direction.SAME_LINE)).contains(spalte()));
    }

    @Test
    public void darunterGibtEsSie() {
        assertTrue(stellen(seiteMit(AnchorRule.Direction.LINE_BELOW)).contains(spalte()));
    }

    @Test
    public void darueberGibtEsSieAuch() {
        assertTrue(stellen(seiteMit(AnchorRule.Direction.LINE_ABOVE)).contains(spalte()));
    }

    /**
     * Abgezählt wird bis zur sechsten Stelle, von links wie von rechts — zwölf Einträge, dazu die
     * Spalte. Die Zahl steht hier, damit ein Kürzen auffällt.
     */
    @Test
    public void sechsStellenJeRichtung() {
        List<String> stellen = stellen(seiteMit(AnchorRule.Direction.LINE_BELOW));
        assertEquals(13, stellen.size());
        assertTrue(stellen.contains(ctx.getString(R.string.statement_rules_nth_right, 6)));
        assertTrue(stellen.contains(ctx.getString(R.string.statement_rules_nth_left, 6)));
    }
}
