package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Die gelernten Erkennungsregeln gehören in die Sicherung.
 *
 * <p>Sie liegen außerhalb der Datenbank, damit „Datenbank zurücksetzen" sie nicht mitnimmt — genau
 * deshalb müssen sie namentlich in {@code BackupStore.PREFS_FILES} stehen. Fehlen sie dort, verliert man
 * beim Einspielen einer Sicherung alles Gelernte, ohne dass es auffällt: die nächste Abrechnung wird
 * einfach wieder nicht vorbelegt.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupStatementRulesTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private static StatementTemplate vorlage() {
        java.util.Map<StatementTemplate.Field, AnchorRule> rules =
                new java.util.EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET,
                AnchorRule.single("Gesamtbetrag zu Ihren Gunsten", AnchorRule.Direction.SAME_LINE, "EUR"));
        rules.put(StatementTemplate.Field.DATE, new AnchorRule(
                java.util.Arrays.asList("Valuta", "Zahltag"), AnchorRule.Direction.SAME_LINE, false));
        return new StatementTemplate("dividend", rules);
    }

    @Test
    public void dasGelernteStehtInDerSicherungUndKommtZurueck() throws Exception {
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        store.save(vorlage());
        store.rememberSecurity("LU1242369327", "Depot", "E000042", "Xtrackers MSCI Europe");

        // Über das Archiv, aber ohne Datenbank: geprüft wird, was an Einstellungen mitgeht.
        BackupArchive.Content content = BackupArchive.read(BackupArchive.write(
                new byte[]{1, 2, 3}, BackupStore.prefsSnapshot(ctx, false), 1));
        assertNotNull("die Regeln müssen in der Sicherung liegen",
                content.prefs("ausgaben_statements"));
        assertTrue(content.prefs("ausgaben_statements").contains("Gesamtbetrag zu Ihren Gunsten"));

        // Alles verwerfen, als hätte man die App neu aufgesetzt.
        store.clearAll();
        assertTrue(new StatementTemplates(ctx).all().isEmpty());
        assertNull(new StatementTemplates(ctx).security("LU1242369327"));

        BackupStore.restoreSettings(ctx, content);

        StatementTemplates back = new StatementTemplates(ctx);
        assertEquals(1, back.all().size());
        assertEquals(java.util.Arrays.asList("Valuta", "Zahltag"),
                back.all().get(0).rule(StatementTemplate.Field.DATE).anchors);
        assertNotNull("auch die Zuordnung zur ISIN", back.security("LU1242369327"));
        assertEquals("E000042", back.security("LU1242369327")[1]);
    }
}
