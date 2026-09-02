package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Wieweit reicht ein Löschen im Vorlagenspeicher?
 *
 * <p>Alle Profile teilen sich eine Einstellungsdatei und stehen darin nur durch den Profilnamen im
 * Schlüssel getrennt; alle Depots eines Profils teilen sich einen Eintrag. Zwei Methoden griffen
 * deshalb weiter, als ihr Name und ihr Javadoc versprachen — und der Schaden fällt nicht auf, denn
 * eine gelöschte Vorlagenliste sieht aus wie eine, die es noch nie gab.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementTemplatesScopeTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Before
    public void resetState() {
        ctx.getSharedPreferences("ausgaben_profiles", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences("ausgaben_statements", Context.MODE_PRIVATE).edit().clear().commit();
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
    }

    private static StatementTemplate vorlage(String anchor) {
        java.util.Map<StatementTemplate.Field, AnchorRule> rules =
                new java.util.EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, new AnchorRule(
                Collections.singletonList(anchor), AnchorRule.Direction.SAME_LINE, false, "",
                AnchorRule.Position.LAST, 1, 0));
        return new StatementTemplate("buy", rules);
    }

    private static List<String> ankerVon(List<StatementTemplate> templates) {
        List<String> out = new java.util.ArrayList<>();
        for (StatementTemplate t : templates) {
            out.add(t.rule(StatementTemplate.Field.NET).anchors.get(0));
        }
        Collections.sort(out);
        return out;
    }

    /** {@code saveAll(List)} sagt „ersetzt das Depot ohne Namen" – und muss die anderen stehen lassen. */
    @Test
    public void dieListenfassungLaesstAndereDepotsInRuhe() {
        StatementTemplates store = new StatementTemplates(ctx);
        store.saveAll("Depot A", Collections.singletonList(vorlage("Kurswert A")));

        store.saveAll(Collections.singletonList(vorlage("Kurswert ohne Depot")));

        assertEquals(Collections.singletonList("Kurswert A"), ankerVon(store.all("Depot A")));
        assertEquals(Collections.singletonList("Kurswert ohne Depot"), ankerVon(store.all("")));
    }

    /** Und beim zweiten Aufruf ersetzt sie ihr eigenes Depot, statt zu häufen. */
    @Test
    public void dieListenfassungErsetztIhrEigenesDepot() {
        StatementTemplates store = new StatementTemplates(ctx);
        store.saveAll(Arrays.asList(vorlage("Erst"), vorlage("Zweit")));

        store.saveAll(Collections.singletonList(vorlage("Nur noch das")));

        assertEquals(Collections.singletonList("Nur noch das"), ankerVon(store.all("")));
    }

    /**
     * Zurücksetzen gilt dem Profil, in dem man steht. Vorher leerte es die ganze Datei und nahm das
     * Gelernte aller anderen Profile mit.
     */
    @Test
    public void zuruecksetzenLaesstDieAnderenProfileInRuhe() {
        ProfileManager pm = new ProfileManager(ctx);
        String erstes = pm.getActiveProfileId();
        new StatementTemplates(ctx).saveAll(Collections.singletonList(vorlage("Erstes Profil")));

        String zweites = pm.createProfile("Zweitprofil").id;
        pm.switchTo(ctx, zweites);
        StatementTemplates imZweiten = new StatementTemplates(ctx);
        imZweiten.saveAll(Collections.singletonList(vorlage("Zweites Profil")));
        imZweiten.clearAll();

        assertTrue("im eigenen Profil ist nichts mehr da", imZweiten.all("").isEmpty());
        pm.switchTo(ctx, erstes);
        assertFalse("im anderen Profil steht das Gelernte weiterhin",
                new StatementTemplates(ctx).all("").isEmpty());
    }
}
