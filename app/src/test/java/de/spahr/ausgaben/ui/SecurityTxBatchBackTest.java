package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import de.spahr.ausgaben.R;

/**
 * Beim Berichtigen eines Stapels darf der Rückweg nichts wegwerfen.
 *
 * <p>Die Maske ist dort nur die Detailansicht einer Zeile der Erkennungsliste; gebucht wird erst in
 * der Liste. Nur „Übernehmen" gab bis 1.12 ein Ergebnis zurück — der Pfeil in der Leiste und der
 * Systemzurück riefen schlicht {@code finish()}. Wer eine Zeile öffnete, den Betrag berichtigte und
 * dann zurückwischte, stand wieder vor derselben roten Zeile, obwohl der Javadoc von
 * {@code returnToList} ausdrücklich verspricht, dass „das Erreichte nicht verloren" geht.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurityTxBatchBackTest {

    /** Eine Zeile der Erkennungsliste, wie die Liste sie zum Berichtigen öffnet. */
    private static Intent stapelIntent() {
        Intent i = new Intent(ApplicationProvider.getApplicationContext(),
                SecurityTxEditActivity.class);
        i.putExtra(SecurityTxEditActivity.EXTRA_DEPOT, "Depot");
        i.putExtra(SecurityTxEditActivity.EXTRA_KMY_ID, "S1");
        i.putExtra(SecurityTxEditActivity.EXTRA_NAME, "Testpapier");
        i.putExtra(SecurityTxEditActivity.EXTRA_BATCH, true);
        return i;
    }

    private static void tippe(SecurityTxEditActivity a, int id, String text) {
        ((android.widget.TextView) a.findViewById(id)).setText(text);
    }

    /** Der Zurück-Pfeil der Leiste: das einzige Bildknopf-Kind der Toolbar. */
    private static android.view.View pfeilInDerLeiste(SecurityTxEditActivity a) {
        androidx.appcompat.widget.Toolbar leiste = a.findViewById(R.id.toolbar);
        for (int i = 0; i < leiste.getChildCount(); i++) {
            if (leiste.getChildAt(i) instanceof android.widget.ImageButton) {
                return leiste.getChildAt(i);
            }
        }
        throw new AssertionError("kein Zurück-Pfeil in der Leiste");
    }

    /** Was die Liste zurückbekommt – oder {@code null}, wenn nichts gesetzt wurde. */
    private static Intent ergebnis(SecurityTxEditActivity a) {
        ShadowActivity shadow = Shadows.shadowOf(a);
        assertEquals("das Ergebnis muss gesetzt sein", Activity.RESULT_OK, shadow.getResultCode());
        return shadow.getResultIntent();
    }

    @Test
    public void derSystemzurueckGibtDenStandAnDieListeZurueck() {
        ActivityController<SecurityTxEditActivity> controller =
                Robolectric.buildActivity(SecurityTxEditActivity.class, stapelIntent()).setup();
        SecurityTxEditActivity a = controller.get();
        tippe(a, R.id.editAccount, "Girokonto");

        a.getOnBackPressedDispatcher().onBackPressed();

        Intent out = ergebnis(a);
        assertNotNull(out);
        assertEquals("das berichtigte Konto kommt mit", "Girokonto",
                out.getStringExtra(SecurityTxEditActivity.EXTRA_PREFILL_ACCOUNT));
        assertTrue("und die Maske ist zu", a.isFinishing());
    }

    /** Derselbe Weg über den Pfeil in der Leiste. */
    @Test
    public void derPfeilInDerLeisteEbenso() {
        ActivityController<SecurityTxEditActivity> controller =
                Robolectric.buildActivity(SecurityTxEditActivity.class, stapelIntent()).setup();
        SecurityTxEditActivity a = controller.get();
        tippe(a, R.id.editAccount, "Tagesgeld");

        pfeilInDerLeiste(a).performClick();

        assertEquals("Tagesgeld", ergebnis(a).getStringExtra(
                SecurityTxEditActivity.EXTRA_PREFILL_ACCOUNT));
    }

    /**
     * Außerhalb des Stapels bleibt es beim Beenden: dort ist die Maske eine eigene Erfassung, und wer
     * sie ohne Speichern verlässt, will nichts anlegen.
     */
    @Test
    public void ohneStapelBleibtZurueckEinAbbruch() {
        Intent i = stapelIntent();
        i.removeExtra(SecurityTxEditActivity.EXTRA_BATCH);
        SecurityTxEditActivity a =
                Robolectric.buildActivity(SecurityTxEditActivity.class, i).setup().get();

        a.getOnBackPressedDispatcher().onBackPressed();

        ShadowActivity shadow = Shadows.shadowOf(a);
        assertEquals("kein Ergebnis", Activity.RESULT_CANCELED, shadow.getResultCode());
        assertTrue(a.isFinishing());
    }
}
