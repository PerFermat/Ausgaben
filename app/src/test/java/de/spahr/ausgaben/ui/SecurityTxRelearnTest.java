package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.statement.AnchorRule;
import de.spahr.ausgaben.statement.StatementScan;
import de.spahr.ausgaben.statement.StatementTemplate;

/**
 * Eine schon gelernte Regel von Hand richtigstellen: Wert ändern, übers Stift-Symbol eine andere
 * Beschriftung wählen, „Lernen" — danach muss die Bank-Vorlage die neue Beschriftung tragen.
 *
 * <p>Der Weg dorthin führt durch drei Stellen, die einander leicht überschreiben: die Live-Suche
 * während der Eingabe, den Stift-Dialog und den Lernvorgang beim Speichern. Genau dort ging die Wahl
 * schon zweimal verloren.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxRelearnTest {

    /** Die Abrechnung: „Stück 30" ist das Gelernte, „Lagerstelle 40" die Zahl, die der Nutzer meint. */
    private static final String BELEG = "Wertpapierabrechnung Kauf\n"
            + "Stück 30\n"
            + "Lagerstelle 40\n"
            + "Kurswert EUR 300,00\n"
            + "Valuta 17.08.2026\n";

    private static AnchorRule kette(String anchor) {
        return new AnchorRule(Arrays.asList(anchor), AnchorRule.Direction.SAME_LINE, false);
    }

    /** Die schon gespeicherte Vorlage für dieses Depot – sie belegt die Maske vor. */
    private static void vorlageAnlegen() {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        rules.put(StatementTemplate.Field.NET, new AnchorRule(Arrays.asList("Kurswert"),
                AnchorRule.Direction.SAME_LINE, false, "EUR"));
        rules.put(StatementTemplate.Field.DATE, kette("Valuta"));
        rules.put(StatementTemplate.Field.SHARES, kette("Stück"));
        new StatementTemplates(ApplicationProvider.getApplicationContext())
                .save(new StatementTemplate(StatementScan.BUY, rules), "Depot");
    }

    private static Intent intent() throws Exception {
        java.io.File beleg = java.io.File.createTempFile("abrechnung", ".txt");
        beleg.deleteOnExit();
        java.nio.file.Files.write(beleg.toPath(),
                BELEG.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Intent i = new Intent(ApplicationProvider.getApplicationContext(),
                SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, "Depot");
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, "S1");
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, "Testpapier");
        i.putExtra(SecurityTxEditActivity.EXTRA_STATEMENT_TEXT, beleg.getAbsolutePath());
        return i;
    }

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** Wartezeit vorspulen – die Suche während der Eingabe läuft mit Verzögerung. */
    private static void warte() throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(50));
            Thread.sleep(5);
        }
    }

    private static void tippe(SecurityTxEditActivity a, int id, String text) {
        ((android.widget.TextView) a.findViewById(id)).setText(text);
    }

    /**
     * Der ganze Weg bis zum Stift-Dialog; zurück kommt er offen.
     *
     * @param wahl welcher Auswahlpunkt angetippt wird (0 = der vorgeschlagene)
     */
    private androidx.appcompat.app.AlertDialog bisZumStift(
            ActivityController<SecurityTxEditActivity> c, int wahl) throws Exception {
        SecurityTxEditActivity a = c.get();
        warte();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);

        a.findViewById(R.id.editDate).performClick();
        android.app.Dialog datum = null;
        for (int i = 0; i < 300 && datum == null; i++) {
            idle();
            datum = ShadowDialog.getLatestDialog();
            Thread.sleep(5);
        }
        assertNotNull("kein Datumsdialog", datum);
        ((androidx.appcompat.app.AlertDialog) datum).getListView().performItemClick(null, 0, 0);
        idle();

        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        tippe(a, R.id.editAccount, "Girokonto");
        // Der Kern des Falls: die Stückzahl wird auf eine Zahl geändert, die woanders im Beleg steht.
        // Mit Fokus wie auf dem Gerät – das Verlassen des Feldes stößt Rechnung und Suche erneut an.
        a.findViewById(R.id.editShares).requestFocus();
        tippe(a, R.id.editShares, "40");
        warte();

        TextInputLayout shares = a.findViewById(R.id.sharesLayout);
        assertEquals("das Stift-Symbol fehlt – die Live-Suche hat nichts gefunden",
                TextInputLayout.END_ICON_CUSTOM, shares.getEndIconMode());
        shares.findViewById(com.google.android.material.R.id.text_input_end_icon).performClick();
        // Wie auf dem Gerät: das Antippen nimmt dem Feld den Fokus, und das rechnet und sucht erneut.
        a.findViewById(R.id.editShares).clearFocus();
        warte();

        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("kein Stift-Dialog", dialog);
        assertEquals("der Ersetzen/Hinzufügen-Schalter fehlt – kein Widerspruch erkannt",
                android.view.View.VISIBLE,
                dialog.findViewById(R.id.anchorReplaceRow).getVisibility());
        if (wahl > 0) {
            android.widget.RadioGroup gruppe = dialog.findViewById(R.id.anchorChoices);
            assertTrue("so viele Kandidaten gibt es nicht", gruppe.getChildCount() > wahl);
            gruppe.getChildAt(wahl).performClick();
            idle();
        }
        return dialog;
    }

    /** Speichern und warten, bis der Lernvorgang durch ist. */
    private static AnchorRule gelernteStueckregel(SecurityTxEditActivity a) throws Exception {
        a.findViewById(R.id.btnSave).performClick();
        for (int i = 0; i < 300; i++) {
            idle();
            Thread.sleep(5);
        }
        return new StatementTemplates(ApplicationProvider.getApplicationContext())
                .all("Depot").get(0).rule(StatementTemplate.Field.SHARES);
    }

    /** Nicht der vorgeschlagene, sondern ein anderer Auswahlpunkt — genau das ging bisher verloren. */
    @Test
    public void auchEinAndererAuswahlpunktWirdGelernt() throws Exception {
        vorlageAnlegen();
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        // Der dritte Eintrag ist „Kurswert — Beschriftung eine Zeile darunter": eine andere
        // Beschriftung UND eine andere Richtung als der Vorschlag ganz oben.
        androidx.appcompat.app.AlertDialog wahl = bisZumStift(c, 2);
        wahl.getButton(android.app.Dialog.BUTTON_POSITIVE).performClick();
        idle();

        AnchorRule stueck = gelernteStueckregel(c.get());
        assertNotNull("die Stückzahl hat gar keine Regel mehr", stueck);
        assertEquals("nicht die angetippte Beschriftung gelernt: " + stueck.anchors,
                Arrays.asList("Kurswert"), stueck.anchors);
        assertEquals(AnchorRule.Direction.LINE_ABOVE, stueck.direction);
    }

    /** Schalter auf „hinzufügen": die alte Beschriftung bleibt, die neue kommt dazu. */
    @Test
    public void mitDemSchalterAufHinzufuegenBleibenBeide() throws Exception {
        vorlageAnlegen();
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        androidx.appcompat.app.AlertDialog wahl = bisZumStift(c, 0);
        ((android.widget.CompoundButton) wahl.findViewById(R.id.anchorReplace)).setChecked(false);
        wahl.getButton(android.app.Dialog.BUTTON_POSITIVE).performClick();
        idle();

        AnchorRule stueck = gelernteStueckregel(c.get());
        assertNotNull("die Stückzahl hat gar keine Regel mehr", stueck);
        assertTrue("die alte Beschriftung ist weg: " + stueck.anchors,
                stueck.anchors.contains("Stück"));
        assertTrue("die neue Beschriftung fehlt: " + stueck.anchors,
                stueck.anchors.contains("Lagerstelle"));
    }

    /** „Nicht lernen": der Wert gilt für diese Buchung, die Vorlage bleibt unangetastet. */
    @Test
    public void nichtLernenLaesstDieVorlageInRuhe() throws Exception {
        vorlageAnlegen();
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        androidx.appcompat.app.AlertDialog wahl = bisZumStift(c, 0);
        wahl.getButton(android.app.Dialog.BUTTON_NEGATIVE).performClick();
        idle();

        assertEquals(Arrays.asList("Stück"), gelernteStueckregel(c.get()).anchors);
    }

    @Test
    public void diePerStiftGewaehlteBeschriftungErsetztDieAlteRegel() throws Exception {
        vorlageAnlegen();
        assertEquals("Vorlage nicht gespeichert", 1, new StatementTemplates(
                ApplicationProvider.getApplicationContext()).all("Depot").size());
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        SecurityTxEditActivity a = c.get();
        warte();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);

        a.findViewById(R.id.editDate).performClick();
        android.app.Dialog datum = null;
        for (int i = 0; i < 300 && datum == null; i++) {
            idle();
            datum = ShadowDialog.getLatestDialog();
            Thread.sleep(5);
        }
        assertNotNull("kein Datumsdialog", datum);
        ((androidx.appcompat.app.AlertDialog) datum).getListView().performItemClick(null, 0, 0);
        idle();

        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        tippe(a, R.id.editAccount, "Girokonto");
        // Der Kern des Falls: die Stückzahl wird auf eine Zahl geändert, die woanders im Beleg steht.
        // Mit Fokus wie auf dem Gerät – das Verlassen des Feldes stößt Rechnung und Suche erneut an.
        a.findViewById(R.id.editShares).requestFocus();
        tippe(a, R.id.editShares, "40");
        warte();

        TextInputLayout shares = a.findViewById(R.id.sharesLayout);
        assertEquals("das Stift-Symbol fehlt – die Live-Suche hat nichts gefunden",
                TextInputLayout.END_ICON_CUSTOM, shares.getEndIconMode());
        shares.findViewById(com.google.android.material.R.id.text_input_end_icon).performClick();
        // Wie auf dem Gerät: das Antippen nimmt dem Feld den Fokus, und das rechnet und sucht erneut.
        a.findViewById(R.id.editShares).clearFocus();
        warte();

        androidx.appcompat.app.AlertDialog wahl =
                (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("kein Stift-Dialog", wahl);
        assertEquals("der Ersetzen/Hinzufügen-Schalter fehlt – kein Widerspruch erkannt",
                android.view.View.VISIBLE,
                wahl.findViewById(R.id.anchorReplaceRow).getVisibility());
        wahl.getButton(android.app.Dialog.BUTTON_POSITIVE).performClick();
        idle();

        a.findViewById(R.id.btnSave).performClick();
        for (int i = 0; i < 300; i++) {
            idle();
            Thread.sleep(5);
        }

        StatementTemplate gelernt = new StatementTemplates(
                ApplicationProvider.getApplicationContext()).all("Depot").get(0);
        AnchorRule stueck = gelernt.rule(StatementTemplate.Field.SHARES);
        assertNotNull("die Stückzahl hat gar keine Regel mehr", stueck);
        assertTrue("gelernt wurde weiterhin " + stueck.anchors,
                stueck.anchors.contains("Lagerstelle"));
    }
}
