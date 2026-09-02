package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.R;

/**
 * „Speichern" darf nur einmal zählen.
 *
 * <p>Zwischen dem Tipp und dem {@code finish()} liegt eine Datenbankschreibung und, wenn aus der
 * Abrechnung gelernt werden kann, noch eine Rückfrage — die Maske steht so lange sichtbar offen. Ohne
 * Sperre legte ein zweiter Tipp Bewegung <b>und</b> Gegenbuchung ein zweites Mal an. Schlimmer noch:
 * beim zweiten Durchlauf war {@code pendingStatement} schon verbraucht, die Abrechnung hing also nur
 * an der ersten der beiden Buchungen — die Doppelung ließ sich hinterher nicht einmal am Beleg
 * erkennen.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxSaveGuardTest {

    private static Intent intent() {
        Intent i = new Intent(ApplicationProvider.getApplicationContext(),
                SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, "Depot");
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, "S1");
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, "Testpapier");
        return i;
    }

    private static void tippe(SecurityTxEditActivity a, int id, String wert) {
        TextInputEditText feld = a.findViewById(id);
        feld.requestFocus();
        feld.setText(wert);
        feld.clearFocus();
    }

    @Test
    public void derKnopfSperrtSichNachDemErstenTipp() {
        SecurityTxEditActivity a =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup().get();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);
        // Ohne Abrechnung steht das heutige Datum bereits fest; es fehlen nur noch Zahlen und Konto.
        tippe(a, R.id.editShares, "10");
        tippe(a, R.id.editPrice, "10,00");
        ((PickerTextView) a.findViewById(R.id.editAccount)).setText("Girokonto");

        assertTrue("Voraussetzung: die Eingabe ist vollständig",
                a.findViewById(R.id.btnSave).isEnabled());

        a.findViewById(R.id.btnSave).performClick();

        assertFalse("ein zweiter Tipp darf nicht noch einmal buchen",
                a.findViewById(R.id.btnSave).isEnabled());
    }

    /** Eine abgewiesene Eingabe darf den Knopf <b>nicht</b> stilllegen – sonst käme man nie weiter. */
    @Test
    public void nachEinerAbgewiesenenEingabeBleibtDerKnopfBedienbar() {
        SecurityTxEditActivity a =
                Robolectric.buildActivity(SecurityTxEditActivity.class, intent()).setup().get();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);
        // Kein Konto, keine Beträge: save() bricht in der Prüfung ab, bevor irgendetwas geschrieben ist.

        a.findViewById(R.id.btnSave).performClick();

        assertTrue(a.findViewById(R.id.btnSave).isEnabled());
    }
}
