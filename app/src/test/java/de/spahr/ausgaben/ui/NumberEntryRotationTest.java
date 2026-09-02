package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.widget.EditText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.R;

/**
 * Die stille Zifferneingabe („Neue Buchung", nur der Betrag) muss eine Drehung überstehen.
 *
 * <p>Der Dialog hing am Fenster der Maske: Beim Drehen war er weg und der eingetippte Betrag mit ihm.
 * Seine Felder entstehen im Code und tragen keine ids — das Fenstersystem kann sie deshalb nicht
 * selbst wiederherstellen, auch nicht als {@link HostedDialog}. Die Maske merkt sich den Stand
 * deshalb ausdrücklich.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NumberEntryRotationTest {

    private static void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** Das Betragsfeld im offenen Dialog – das einzige {@link EditText} darin. */
    private static EditText betragsfeld(Dialog dialog) {
        return sucheEditText(dialog.getWindow().getDecorView());
    }

    private static EditText sucheEditText(android.view.View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText treffer = sucheEditText(g.getChildAt(i));
                if (treffer != null) {
                    return treffer;
                }
            }
        }
        return null;
    }

    private static Dialog offenerDialog(MainActivity a) {
        androidx.fragment.app.Fragment f =
                a.getSupportFragmentManager().findFragmentByTag("dlg_numberEntry");
        return f == null ? null : ((androidx.fragment.app.DialogFragment) f).getDialog();
    }

    @Test
    public void derEingetippteBetragUeberstehtDieDrehung() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        a.findViewById(R.id.fabNumber).performClick();
        idle();

        Dialog vorher = offenerDialog(a);
        assertNotNull("der Dialog steht offen", vorher);
        EditText feld = betragsfeld(vorher);
        assertNotNull("mit einem Betragsfeld", feld);
        feld.setText("12,50");

        c.recreate();
        idle();
        MainActivity nachher = c.get();

        Dialog wieder = offenerDialog(nachher);
        assertNotNull("der Dialog steht nach der Drehung wieder da", wieder);
        assertTrue("und ist sichtbar", wieder.isShowing());
        assertEquals("mit dem eingetippten Betrag", "12,50",
                betragsfeld(wieder).getText().toString());
    }

    /**
     * Und weggetippt heißt verworfen: Beim nächsten Öffnen soll nicht der alte Betrag dastehen. Ohne
     * das wäre aus der Sicherung des Standes eine neue Falle geworden.
     */
    @Test
    public void nachDemWegtippenIstDasFeldWiederLeer() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        a.findViewById(R.id.fabNumber).performClick();
        idle();
        betragsfeld(offenerDialog(a)).setText("99,00");

        offenerDialog(a).cancel();
        idle();
        a.findViewById(R.id.fabNumber).performClick();
        idle();

        assertEquals("", betragsfeld(offenerDialog(a)).getText().toString());
    }
}
