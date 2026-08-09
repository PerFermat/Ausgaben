package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Filterable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Der Suchlauf der Vorschlagslisten. Er streicht den Bestand des Adapters selbst zusammen – und muß ihn
 * bei leerem Begriff vollständig wiederherstellen, sonst stünden beim nächsten Öffnen des Feldes nur
 * noch die Treffer der vorigen Suche da.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PickerFilterTest {

    private static final List<String> KONTEN = Arrays.asList(
            "Girokonto", "Visa", "Visa Urlaub", "Sparkasse");

    private ArrayAdapter<String> adapter() {
        Context ctx = ApplicationProvider.getApplicationContext();
        return PickerAdapters.plainAdapter(ctx, KONTEN);
    }

    /** Sucht und wartet, bis das Ergebnis im Adapter steht – der Suchlauf läuft nebenher. */
    private void suche(ArrayAdapter<String> adapter, String query) throws InterruptedException {
        CountDownLatch fertig = new CountDownLatch(1);
        ((Filterable) adapter).getFilter().filter(query, count -> fertig.countDown());
        for (int i = 0; i < 50 && fertig.getCount() > 0; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (fertig.await(20, TimeUnit.MILLISECONDS)) {
                break;
            }
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private List<String> inhalt(ArrayAdapter<String> adapter) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            out.add(adapter.getItem(i));
        }
        return out;
    }

    @Test
    public void teiltrefferAuchMittenImNamen() throws Exception {
        ArrayAdapter<String> adapter = adapter();

        // Der ArrayAdapter prüft von Haus aus nur Wortanfänge – „kasse" fände „Sparkasse" dann nicht.
        suche(adapter, "kasse");

        assertEquals(Collections.singletonList("Sparkasse"), inhalt(adapter));
    }

    @Test
    public void grossKleinschreibungIstEgal() throws Exception {
        ArrayAdapter<String> adapter = adapter();

        suche(adapter, "VISA");

        assertEquals(Arrays.asList("Visa", "Visa Urlaub"), inhalt(adapter));
    }

    @Test
    public void leererBegriffStelltDenGanzenBestandWiederHer() throws Exception {
        ArrayAdapter<String> adapter = adapter();

        suche(adapter, "vi");
        assertEquals(Arrays.asList("Visa", "Visa Urlaub"), inhalt(adapter));

        // Der Fehlerfall: nach einer Suche ein Konto wählen und das Feld gleich wieder öffnen.
        suche(adapter, "");
        assertEquals(KONTEN, inhalt(adapter));
    }

    @Test
    public void mehrereSuchenNacheinanderEngenNichtImmerWeiterEin() throws Exception {
        ArrayAdapter<String> adapter = adapter();

        suche(adapter, "visa");
        suche(adapter, "giro");

        assertEquals(Collections.singletonList("Girokonto"), inhalt(adapter));
    }
}
