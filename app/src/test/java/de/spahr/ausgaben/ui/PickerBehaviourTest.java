package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

    /**
     * Die Fertig-Taste der Tastatur. Vorher {@link #ruhe()}, damit der Suchlauf zum getippten Begriff
     * durch ist – erst dann steht im Adapter die Trefferliste, die der Benutzer vor sich hätte.
     */
    private void fertig() {
        ruhe();
        field.onEditorAction(EditorInfo.IME_ACTION_DONE);
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

    // ---- Die Fertig-Taste ----

    /** Unten rechts steht in jedem Vorschlagsfeld dieselbe Taste, gleich wo das Feld sitzt. */
    @Test
    public void jedesVorschlagsfeldHatEineFertigTaste() {
        feld(PickerBehaviour.Unknown.RESTORE);

        assertEquals(EditorInfo.IME_ACTION_DONE,
                field.getImeOptions() & EditorInfo.IME_MASK_ACTION);
    }

    @Test
    public void fertigUebernimmtDenEinzigenTreffer() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();
        field.setText("visa u");
        fertig();

        assertEquals("Visa Urlaub", field.getText().toString());
    }

    /**
     * Bleiben mehrere übrig – „vis" trifft „Visa" und „Visa Urlaub" –, ist nichts entschieden, und es
     * gilt die alte Regel: der beiseite gelegte Wert kommt zurück.
     */
    @Test
    public void fertigEntscheidetNichtBeiMehrerenTreffern() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();
        field.setText("vis");
        fertig();

        assertEquals("Girokonto", field.getText().toString());
    }

    /**
     * „Visa" ist zugleich ein Kontoname und der Anfang von „Visa Urlaub". Der genaue Name entscheidet –
     * sonst käme man an ein Konto, dessen Name in einem anderen steckt, mit der Tastatur nie heran.
     */
    @Test
    public void derGenaueNameSchlaegtDieMehrdeutigkeit() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();
        field.setText("visa");
        fertig();

        assertEquals("Visa", field.getText().toString());
    }

    @Test
    public void fertigNimmtDenGenauenNamenInDerSchreibweiseDerListe() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);

        hinein();
        field.setText("  sparkasse  ");
        fertig();

        assertEquals("Sparkasse", field.getText().toString());
    }

    /**
     * Im leeren Feld steht der ganze Bestand. Daß dort zufällig nur ein Konto stünde, hieße nicht, daß
     * der Benutzer es wählen will – geprüft wird das mit einem Feld, dessen Liste genau einen Eintrag hat.
     */
    @Test
    public void fertigImLeerenFeldWaehltNichtsAus() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        layout = new TextInputLayout(activity);
        field = new PickerTextView(activity);
        layout.addView(field);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(layout);
        PickerAdapters.attach(field,
                PickerAdapters.plainAdapter(activity, Arrays.asList("Girokonto")));
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
        field.setText("Visa", false);

        hinein();
        fertig();

        assertEquals("Visa", field.getText().toString());
    }

    @Test
    public void fertigLaesstEinenNeuenEmpfaengerStehen() {
        feld(PickerBehaviour.Unknown.KEEP);
        field.setText("Visa", false);

        hinein();
        field.setText("Bäckerei Müller");
        fertig();

        assertEquals("Bäckerei Müller", field.getText().toString());
    }

    /** Nach der Fertig-Taste ist die Suche beendet – sonst stünde das Feld leer mit blassem Platzhalter da. */
    @Test
    public void fertigBeendetDieSuche() {
        feld(PickerBehaviour.Unknown.RESTORE);
        field.setText("Girokonto", false);
        final String[] gemeldet = {null};
        PickerBehaviour.onCommitted(field, value -> gemeldet[0] = value);

        hinein();
        field.setText("visa u");
        fertig();

        assertEquals("Der Platzhalter verschwindet wieder", null, layout.getPlaceholderText());
        assertEquals("und der neue Wert wird gemeldet", "Visa Urlaub", gemeldet[0]);
    }
}
