package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import de.spahr.ausgaben.R;

/**
 * Über allen Zahlenfeldern der Erfassungsmaske sitzt dieselbe Rechentastatur — dann muss auch derselbe
 * Parser darunter liegen.
 *
 * <p>Bis 1.12 lief „Anzahl" über {@code Double.parseDouble}, die Betragsfelder dagegen über
 * {@code AmountExpression}. Wer die Stückzahl als {@code 10*3} eintippte — was die Tastatur
 * ausdrücklich anbietet —, bekam beim Speichern „Beträge fehlen", obwohl sichtbar etwas im Feld
 * stand.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxNumberParserTest {

    private static Intent intent() {
        Intent i = new Intent(ApplicationProvider.getApplicationContext(),
                SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, "Depot");
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, "S1");
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, "Testpapier");
        return i;
    }

    /** Ein Kauf mit allem, was {@code save()} vor den Beträgen prüft (Art und Datum stehen dann). */
    private static SecurityTxEditActivity kaufmaske() {
        SecurityTxEditActivity a = Robolectric.buildActivity(
                SecurityTxEditActivity.class, intent()).setup().get();
        ((MaterialButtonToggleGroup) a.findViewById(R.id.toggleAction)).check(R.id.btnBuy);
        return a;
    }

    private static void tippe(SecurityTxEditActivity a, int id, String text) {
        ((android.widget.TextView) a.findViewById(id)).setText(text);
    }

    private static String meldungNachSpeichern(SecurityTxEditActivity a) {
        ShadowToast.reset();
        a.findViewById(R.id.btnSave).performClick();
        return ShadowToast.getTextOfLatestToast();
    }

    @Test
    public void eineRechnungInDerAnzahlWirdGelesen() {
        SecurityTxEditActivity a = kaufmaske();
        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        tippe(a, R.id.editShares, "10*3");

        assertNotEquals("30 Stück stehen im Feld – sie dürfen nicht als fehlend gelten",
                a.getString(R.string.security_tx_need_amounts), meldungNachSpeichern(a));
    }

    /** Dieselbe Eingabe mit Komma und Plus — auch das bietet die Tastatur an. */
    @Test
    public void auchSummeUndKommaWerdenGelesen() {
        SecurityTxEditActivity a = kaufmaske();
        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        tippe(a, R.id.editShares, "6,5+3,5");

        assertNotEquals(a.getString(R.string.security_tx_need_amounts), meldungNachSpeichern(a));
    }

    /**
     * Und was wirklich fehlt, wird am Feld markiert statt in einem Sammel-Toast erwähnt: bei fünf
     * Zahlenfeldern untereinander war „Beträge fehlen" eine Suchaufgabe.
     */
    @Test
    public void dasFehlendeFeldWirdMarkiert() {
        SecurityTxEditActivity a = kaufmaske();
        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        // Anzahl bleibt leer

        assertEquals(a.getString(R.string.security_tx_need_amounts), meldungNachSpeichern(a));
        assertNotNull("die Anzahl ist markiert", fehler(a, R.id.sharesLayout));
        assertNull("der Bruttobetrag steht ja da", fehler(a, R.id.grossLayout));
    }

    /** Wer den Wert nachträgt, wird die Markierung sofort wieder los. */
    @Test
    public void dieMarkierungVerschwindetBeimTippen() {
        SecurityTxEditActivity a = kaufmaske();
        tippe(a, R.id.editGross, "300,00");
        tippe(a, R.id.editNet, "300,00");
        meldungNachSpeichern(a);
        assertNotNull(fehler(a, R.id.sharesLayout));

        tippe(a, R.id.editShares, "30");

        assertNull(fehler(a, R.id.sharesLayout));
    }

    private static CharSequence fehler(SecurityTxEditActivity a, int layoutId) {
        return ((TextInputLayout) a.findViewById(layoutId)).getError();
    }
}
