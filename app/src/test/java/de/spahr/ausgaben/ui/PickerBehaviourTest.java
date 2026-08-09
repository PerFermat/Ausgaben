package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Das Verhalten eines Vorschlagsfeldes: leeren beim Hineingehen, entscheiden beim Verlassen.
 *
 * <p>Das Aufklappen der Liste selbst läßt sich hier nicht prüfen – unter Robolectric gilt das Fenster
 * als unsichtbar, und Androids eigener Zuklapp-Pfad steigt dann vorzeitig aus. Geprüft wird deshalb der
 * Zustand von Feld, Platzhalter und Adapter.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PickerBehaviourTest {

    private static final List<String> KONTEN = Arrays.asList(
            "Girokonto", "Visa", "Visa Urlaub", "Sparkasse");

    private TextInputLayout layout;
    private AutoCompleteTextView field;

    private void feld(PickerBehaviour.Unknown unknown) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        layout = new TextInputLayout(activity);
        field = new PickerTextView(activity);
        layout.addView(field);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(layout);
        ArrayAdapter<String> adapter = PickerAdapters.plainAdapter(activity, KONTEN);
        PickerAdapters.attach(field, adapter);
        PickerBehaviour.searchable(field, unknown);
    }

    /** Läßt den Suchlauf und alles, was er nach sich zieht, zu Ende laufen. */
    private void ruhe() {
        for (int i = 0; i < 20; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private void hinein() {
        field.requestFocus();
        ruhe();
    }

    private void hinaus() {
        PickerBehaviour.settle(field);
        ruhe();
    }

    @Test
    public void beimHineingehenWirdGeleertUndDerAlteWertWirdZumPlatzhalter() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();

        assertEquals("Der bisherige Eintrag macht dem Suchbegriff Platz", "",
                field.getText().toString());
        assertEquals("Girokonto", String.valueOf(layout.getPlaceholderText()));
        assertEquals("Die ganze Liste steht zur Auswahl", KONTEN.size(), field.getAdapter().getCount());
    }

    @Test
    public void leeresFeldHoltDenAltenWertZurueck() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Visa", false);

        hinein();
        hinaus();

        assertEquals("Visa", field.getText().toString());
        assertEquals("Der Platzhalter verschwindet wieder", null, layout.getPlaceholderText());
    }

    @Test
    public void unbekannterTextWirdBeiKontoUndKategorieZurueckgesetzt() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Visa", false);

        hinein();
        field.setText("Sparbuch");
        hinaus();

        assertEquals("Visa", field.getText().toString());
    }

    @Test
    public void unbekannterTextBleibtBeimEmpfaengerStehen() {
        feld(PickerBehaviour.Unknown.KEEP);
        field.setText("Visa", false);

        hinein();
        field.setText("Bäckerei Müller");
        hinaus();

        assertEquals("Bäckerei Müller", field.getText().toString());
    }

    @Test
    public void auchBeimEmpfaengerHoltEinLeeresFeldDenAltenWertZurueck() {
        feld(PickerBehaviour.Unknown.KEEP);
        field.setText("Visa", false);

        hinein();
        hinaus();

        assertEquals("Visa", field.getText().toString());
    }

    @Test
    public void einBekannterEintragWirdUebernommenUndZwarInDerSchreibweiseDerListe() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();
        field.setText("  sparkasse  ");
        hinaus();

        assertEquals("Sparkasse", field.getText().toString());
    }

    /**
     * Ein getippter und stehengelassener Name muß genauso gemeldet werden wie ein angetippter Eintrag –
     * sonst verpassen ihn die Aufrufer, die daran etwas nachziehen (Standardort, Ortslisten, Sicht der
     * Auswertung). Das war der Grund, {@code setOnItemClickListener} abzulösen.
     */
    @Test
    public void auchDerGetippteWertWirdGemeldet() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);
        final String[] gemeldet = {null};
        PickerBehaviour.onCommitted(field, value -> gemeldet[0] = value);

        hinein();
        field.setText("sparkasse");
        hinaus();

        assertEquals("Sparkasse", gemeldet[0]);
    }

    @Test
    public void einUnveraenderterWertWirdNichtGemeldet() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Visa", false);
        final String[] gemeldet = {null};
        PickerBehaviour.onCommitted(field, value -> gemeldet[0] = value);

        hinein();
        hinaus();

        assertEquals(null, gemeldet[0]);
    }

    /**
     * Der Grund für die eigene Feldklasse: über die Schwelle ist er nicht zu erreichen, denn
     * {@code setThreshold(0)} hebt {@code AutoCompleteTextView} stillschweigend wieder auf 1 an. Ohne
     * diese Antwort klappte die gerade geöffnete Liste beim leeren Feld sofort wieder zu.
     */
    @Test
    public void auchDasLeereFeldGiltAlsSuchbereit() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();

        assertEquals("", field.getText().toString());
        assertTrue(field.enoughToFilter());
    }
}
