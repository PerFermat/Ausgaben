package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Account;
import de.spahr.ausgaben.db.AppDatabase;

/**
 * Die Umbuchung im Buchungseditor: zwei Konten statt Kategorien.
 *
 * <p>Der Speichern-Knopf ist hier die ganze Prüfung — er entscheidet, ob die Eingabe zusammenpasst,
 * und er ist die einzige Rückmeldung, die der Nutzer bekommt, bevor er drückt. Vier Bedingungen müssen
 * dafür zugleich gelten, und jede einzelne davon von Hand durchzuspielen ist die Art Fleißarbeit, die
 * nach einer Änderung niemand mehr macht.</p>
 *
 * <p>Die Konten kommen aus der Datenbank und damit erst nach dem Aufbau der Maske; der Test wartet
 * darauf, wie es der Nutzer auch tut.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TransferBookingTest {

    private BookingEditActivity activity;
    private AutoCompleteTextView von;
    private AutoCompleteTextView nach;
    private TextInputEditText betrag;
    private MaterialButton speichern;

    @Before
    public void maske() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(ctx);
        Thread anlegen = new Thread(() -> {
            db.accountDao().insertIfAbsent(new Account("Giro"));
            db.accountDao().insertIfAbsent(new Account("Sparbuch"));
        });
        anlegen.start();
        anlegen.join();

        activity = Robolectric.buildActivity(BookingEditActivity.class).setup().get();
        warteAufDieKonten();
        von = activity.findViewById(R.id.editAccount);
        nach = activity.findViewById(R.id.editAccountTo);
        betrag = activity.findViewById(R.id.editAmount);
        speichern = activity.findViewById(R.id.btnSaveNew);
    }

    /** Die Kontenliste wird nebenher geladen; ohne sie kennt die Maske kein einziges Konto. */
    private void warteAufDieKonten() throws InterruptedException {
        AutoCompleteTextView konto = activity.findViewById(R.id.editAccount);
        for (int i = 0; i < 50 && (konto.getAdapter() == null
                || konto.getAdapter().getCount() == 0); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void umbuchung() {
        ((MaterialButtonToggleGroup) activity.findViewById(R.id.toggleType)).check(R.id.btnTransfer);
    }

    // ---- Was die Umschaltung mit der Maske macht ----

    @Test
    public void umbuchungZeigtDasZweiteKontoUndVerstecktDieKategorien() {
        assertEquals("vorher verdeckt", View.GONE,
                activity.findViewById(R.id.accountToLayout).getVisibility());

        umbuchung();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.accountToLayout).getVisibility());
        assertEquals("eine Umbuchung hat keine Kategorien", View.GONE,
                activity.findViewById(R.id.splitSection).getVisibility());
    }

    @Test
    public void zurueckZurAusgabeHoltDieKategorienWieder() {
        umbuchung();
        ((MaterialButtonToggleGroup) activity.findViewById(R.id.toggleType)).check(R.id.btnExpense);

        assertEquals(View.VISIBLE, activity.findViewById(R.id.splitSection).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.accountToLayout).getVisibility());
    }

    // ---- Wann sich speichern lässt ----

    @Test
    public void vollstaendigeUmbuchungLaesstSichSpeichern() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Sparbuch");
        betrag.setText("50,00");

        assertTrue(speichern.isEnabled());
    }

    /** Von einem Konto auf dasselbe ist keine Buchung, sondern ein Vertipper. */
    @Test
    public void dasselbeKontoZweimalGehtNicht() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Giro");
        betrag.setText("50,00");

        assertFalse(speichern.isEnabled());
    }

    /** Auch nicht mit anderer Groß- und Kleinschreibung — es ist immer noch dasselbe Konto. */
    @Test
    public void dasselbeKontoAndersGeschriebenGehtAuchNicht() {
        umbuchung();
        von.setText("Giro");
        nach.setText("giro");
        betrag.setText("50,00");

        assertFalse(speichern.isEnabled());
    }

    /** Ein Konto, das es nicht gibt, ist kein Ziel — hier hilft kein Tippfehler weiter. */
    @Test
    public void unbekanntesKontoGehtNicht() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Bausparen");
        betrag.setText("50,00");

        assertFalse(speichern.isEnabled());
    }

    @Test
    public void ohneBetragGehtNichts() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Sparbuch");

        assertFalse(speichern.isEnabled());
    }

    @Test
    public void nullEuroIstKeineUmbuchung() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Sparbuch");
        betrag.setText("0,00");

        assertFalse(speichern.isEnabled());
    }

    /** Und der Weg zurück: wird der Mangel behoben, gibt der Knopf von selbst wieder frei. */
    @Test
    public void nachDerBerichtigungGehtEsWieder() {
        umbuchung();
        von.setText("Giro");
        nach.setText("Giro");
        betrag.setText("50,00");
        assertFalse(speichern.isEnabled());

        nach.setText("Sparbuch");

        assertTrue(speichern.isEnabled());
    }
}
