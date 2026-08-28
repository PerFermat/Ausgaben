package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Die Regelseite mit einer Abrechnung im Intent: der Weg aus der Rückmeldung nach dem Merken.
 *
 * <p>Sagt die App „die Regeln greifen noch nicht", ist der Knopf daneben nur etwas wert, wenn die
 * Abrechnung auf der Regelseite schon hängt. Sonst müsste man die Datei von Hand wieder heraussuchen —
 * und genau daran scheitert das Nachbessern.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementRulesTestFileTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    /** Die Testdatei in den Zwischenspeicher legen; von dort liest die Seite sie wie eine gewählte. */
    private Uri pdf(String name) throws IOException {
        File ziel = new File(ctx.getCacheDir(), name);
        try (InputStream in = getClass().getResourceAsStream("/pdf/" + name)) {
            assertNotNull("Testdatei fehlt: " + name, in);
            Files.copy(in, ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return Uri.fromFile(ziel);
    }

    /** Eine Vorlage, die den Gesamtbetrag über die Spalte der Überschrift „Gesamt" liest. */
    private void vorlageAnlegen() {
        StatementTemplates store = new StatementTemplates(ctx);
        store.clearAll();
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, new AnchorRule(
                Collections.singletonList("Gesamt"), AnchorRule.Direction.LINE_BELOW, false, "",
                AnchorRule.Position.COLUMN, 1, 1));
        store.saveAll(Collections.singletonList(new StatementTemplate("buy", rules)));
    }

    @Test
    public void dieAbrechnungAusDemIntentHaengtSofortAlsProbe() throws IOException {
        vorlageAnlegen();
        Intent intent = new Intent(ctx, StatementRulesActivity.class);
        intent.setData(pdf("tabelle.pdf"));
        StatementRulesActivity activity =
                Robolectric.buildActivity(StatementRulesActivity.class, intent).setup().get();
        // Das Einlesen läuft nebenher; hier wird darauf gewartet.
        ShadowLooper.idleMainLooper();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        TextView datei = activity.findViewById(R.id.textTestFile);
        assertEquals(View.VISIBLE, datei.getVisibility());
        assertTrue("der Dateiname steht da", datei.getText().toString().contains("tabelle.pdf"));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.testButtons).getVisibility());

        // Und der Bereich für den Gesamtbetrag zeigt, was seine Regel darin liest.
        ViewGroup container = activity.findViewById(R.id.fieldContainer);
        TextView gefunden = container.getChildAt(1).findViewById(R.id.textFieldFound);
        assertTrue("gelesen wird der Betrag der Tabelle: " + gefunden.getText(),
                gefunden.getText().toString().contains("999,00"));
    }

    /** Ohne Abrechnung im Intent bleibt die Seite, wie sie war — der übliche Weg über das Depot. */
    @Test
    public void ohneAbrechnungBleibtDieProbeLeer() {
        vorlageAnlegen();
        StatementRulesActivity activity = Robolectric.buildActivity(StatementRulesActivity.class,
                new Intent(ctx, StatementRulesActivity.class)).setup().get();
        assertEquals(View.GONE, activity.findViewById(R.id.textTestFile).getVisibility());
    }
}
