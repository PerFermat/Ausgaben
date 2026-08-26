package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Die vorbelegte Steuer einer Dividende muss sich löschen lassen.
 *
 * <p>Der Steuersatz aus den Einstellungen ist eine Hilfe beim Eintippen — solange sie nicht dazwischen
 * greift. Rechnete die Maske sofort nach, wenn das Feld leer wird, käme mit dem letzten gelöschten
 * Zeichen die Vorbelegung zurück, und eine 0 ließe sich überhaupt nicht eintragen. Genau das war der
 * Fall: eine Ausschüttung innerhalb des Freibetrags war von Hand nicht erfassbar.</p>
 *
 * <p>Die Regel lautet deshalb: in das Feld, in dem der Nutzer gerade steht, schreibt die Rechnung
 * nicht. Erst beim Verlassen entscheidet sich, ob die Vorbelegung zurückkommt — und das tut sie nur,
 * wenn das Feld dann leer ist.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DividendTaxFieldTest {

    private SecurityTxEditActivity activity;
    private TextInputEditText gross;
    private TextInputEditText fee;

    @Before
    public void maske() {
        new SettingsStore(ApplicationProvider.getApplicationContext())
                .setDividendTaxPercent(26.375);
        Intent i = new Intent(ApplicationProvider.getApplicationContext(),
                SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, "Depot");
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, "S1");
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, "Testpapier");
        activity = Robolectric.buildActivity(SecurityTxEditActivity.class, i).setup().get();
        MaterialButtonToggleGroup toggle = activity.findViewById(R.id.toggleAction);
        toggle.check(R.id.btnDividend);
        gross = activity.findViewById(R.id.editGross);
        fee = activity.findViewById(R.id.editFee);
        gross.requestFocus();
        gross.setText("100,00");
        gross.clearFocus();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /** Die Vorbelegung selbst — ohne sie hätte der Rest des Tests keinen Gegenstand. */
    @Test
    public void dieSteuerWirdVorbelegt() {
        assertTrue("keine Steuer vorbelegt: " + text(fee), text(fee).startsWith("26,3"));
    }

    @Test
    public void leerenImFeldBleibtLeer() {
        fee.requestFocus();
        fee.setText("");

        assertEquals("", text(fee));
    }

    /** Der eigentliche Fall: eine Ausschüttung, von der nichts abgezogen wurde. */
    @Test
    public void nullLaesstSichEintragenUndBleibtStehen() {
        fee.requestFocus();
        fee.setText("");
        fee.setText("0");
        fee.clearFocus();

        assertEquals("0", text(fee));
    }

    /** Wer das Feld leer verlässt, wollte keine Angabe machen — dann hilft die Vorbelegung wieder. */
    @Test
    public void leerVerlassenHoltDieVorbelegungZurueck() {
        fee.requestFocus();
        fee.setText("");
        fee.clearFocus();

        assertTrue("Vorbelegung kam nicht zurück: " + text(fee), text(fee).startsWith("26,3"));
    }
}
