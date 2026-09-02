package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.DialogInterface;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import de.spahr.ausgaben.R;

/**
 * Eine Drehung mitten in der Lern-Rückfrage darf die Maske nicht als Sackgasse zurücklassen.
 *
 * <p>Nach dem Speichern steht die Bewegung in der Datenbank; die Maske bleibt nur noch für die
 * Rückfrage offen, ob die App aus dieser Abrechnung eine Bank-Vorlage lernen soll. Dieser Dialog hing
 * bis 1.12 am Fenster der Activity. Wurde gedreht, war er weg — und mit ihm der einzige Weg zum
 * {@code finish()}. Zurück blieb eine ausgefüllte Maske mit gesperrtem Speichern-Knopf, aus der man
 * nur mit der Zurück-Taste herauskam; die Vorlage wurde nicht gelernt.</p>
 *
 * <p>Eine zweite Buchung war dabei <b>nicht</b> möglich — {@code onSaveInstanceState} sichert
 * {@code saving} mit, der Knopf bleibt gesperrt. Das gehört zum Bild, weil der ursprüngliche Befund
 * das anders behauptet hat.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxLearnRotationTest {

    /** Eine Maske aus einer eingelesenen Abrechnung – nur so gibt es überhaupt etwas zu lernen. */
    private static Intent intent() throws Exception {
        java.io.File beleg = java.io.File.createTempFile("abrechnung", ".txt");
        beleg.deleteOnExit();
        java.nio.file.Files.write(beleg.toPath(),
                "Wertpapierabrechnung Kauf\nKurswert EUR 300,00\nValuta 17.08.2026\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

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

    /** Auf einen Dialog warten – das Einlesen der Abrechnung läuft im Hintergrund. */
    private static android.app.Dialog warteAufDialog() throws InterruptedException {
        for (int versuch = 0; versuch < 300; versuch++) {
            idle();
            android.app.Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null) {
                return dialog;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("kein Dialog");
    }

    private static void tippe(SecurityTxEditActivity a, int id, String text) {
        ((android.widget.TextView) a.findViewById(id)).setText(text);
    }

    /** Eine vollständig ausgefüllte Kaufmaske, gespeichert – danach läuft die Lern-Rückfrage. */
    private static ActivityController<SecurityTxEditActivity> gespeicherteMaske() throws Exception {
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        SecurityTxEditActivity a = c.get();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);

        // Datum aus der Abrechnung wählen – ohne das kommt save() nicht bis zu den Beträgen.
        a.findViewById(R.id.editDate).performClick();
        android.app.Dialog datum = warteAufDialog();
        ((androidx.appcompat.app.AlertDialog) datum).getListView().performItemClick(null, 0, 0);
        idle();

        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        tippe(a, R.id.editShares, "30");
        tippe(a, R.id.editAccount, "Girokonto");

        a.findViewById(R.id.btnSave).performClick();
        idle();
        assertFalse("gespeichert – die Maske wartet nur noch auf die Rückfrage", a.isFinishing());
        assertFalse("und der Speichern-Knopf ist gesperrt", a.findViewById(R.id.btnSave).isEnabled());
        return c;
    }

    /**
     * Nach der Drehung steht die Lern-Rückfrage wieder da.
     *
     * <p>Sie lässt sich nicht einfach wiederherstellen: Ihr Text hängt an einem gelesenen PDF und an
     * den daraus gelernten Regeln, und beides lag nur im Speicher der alten Maske — in ein
     * {@code Bundle} gehört es nicht. Die Maske geht den Weg deshalb noch einmal: Abrechnung im
     * Hintergrund erneut lesen, erneut lernen, Frage erneut stellen.</p>
     */
    @Test
    public void nachDerDrehungStehtDieLernRueckfrageWiederDa() throws Exception {
        ActivityController<SecurityTxEditActivity> c = gespeicherteMaske();

        c.recreate();
        SecurityTxEditActivity nachher = c.get();

        androidx.fragment.app.Fragment frage = null;
        for (int versuch = 0; versuch < 300; versuch++) {
            idle();
            frage = nachher.getSupportFragmentManager().findFragmentByTag("dlg_learn");
            if (frage != null && ((androidx.fragment.app.DialogFragment) frage).getDialog() != null
                    && ((androidx.fragment.app.DialogFragment) frage).getDialog().isShowing()) {
                break;
            }
            Thread.sleep(10);
        }

        assertNotNull("die Rückfrage ist wieder da", frage);
        assertTrue("und steht sichtbar offen",
                ((androidx.fragment.app.DialogFragment) frage).getDialog().isShowing());
        assertFalse("die Maske ist also keine Sackgasse", nachher.isFinishing());
    }

    /**
     * Und der Speichern-Knopf bleibt über die Drehung gesperrt: Eine zweite Buchung ist nicht
     * auslösbar. Das prüft die Zusicherung mit, die der Fix zu Punkt 4 mitgebracht hat.
     */
    @Test
    public void einZweiterTippLegtNichtsEinZweitesMalAn() throws Exception {
        ActivityController<SecurityTxEditActivity> c = gespeicherteMaske();

        c.recreate();
        SecurityTxEditActivity nachher = c.get();
        idle();

        assertFalse("gesperrt", nachher.findViewById(R.id.btnSave).isEnabled());
    }

    /**
     * Der Kalender ist jetzt ein {@link HostedDialog} und steht nach der Drehung wieder da — vorher war
     * er weg und die Eingabe von vorn zu machen.
     */
    @Test
    public void derKalenderStehtNachDerDrehungWiederDa() throws Exception {
        ActivityController<SecurityTxEditActivity> c =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup();
        SecurityTxEditActivity a = c.get();
        a.findViewById(R.id.editDate).performClick();
        androidx.appcompat.app.AlertDialog auswahl =
                (androidx.appcompat.app.AlertDialog) warteAufDialog();
        // Der letzte Eintrag der Liste ist „Anderes Datum" und öffnet den Kalender.
        int letzter = auswahl.getListView().getCount() - 1;
        auswahl.getListView().performItemClick(null, letzter, letzter);
        idle();
        assertTrue("der Kalender steht offen",
                ShadowDialog.getLatestDialog() instanceof android.app.DatePickerDialog);

        c.recreate();
        idle();

        assertTrue("und steht nach der Drehung wieder da",
                ShadowDialog.getLatestDialog() instanceof android.app.DatePickerDialog);
        ShadowDialog.getLatestDialog().dismiss();
    }

    /** Nicht vergessen: der weggetippte Dialog beendet die Maske weiterhin. */
    @Test
    public void derWeggetippteDialogBeendetDieMaske() throws Exception {
        ActivityController<SecurityTxEditActivity> c = gespeicherteMaske();
        android.app.Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog != null && dialog.isShowing()) {
            dialog.cancel();
            idle();
        }
        // Ob über den Dialog oder über den Abschluss des Lernvorgangs: die Maske schließt.
        for (int versuch = 0; versuch < 300 && !c.get().isFinishing(); versuch++) {
            idle();
            Thread.sleep(10);
        }
        assertTrue(c.get().isFinishing());
    }

    @SuppressWarnings("unused")
    private static void unbenutzt(DialogInterface d) {
    }
}
