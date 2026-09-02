package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.spahr.ausgaben.R;

/**
 * Maße und Anordnung der eigenen Rechentastatur, quer und hoch.
 *
 * <p>Fünf Reihen zu 54dp sind rund 300dp — im Querformat bliebe von gut 400dp Bildschirmhöhe kaum
 * etwas für das Formular. Quer bekommt die Tastatur deshalb eine eigene Anordnung: drei Reihen zu
 * sechs Spalten, doppelt so breit wie hoch, als flaches Feld unten in der Mitte.</p>
 *
 * <p>Der erste Anlauf machte sie quer nur kleiner und behielt die fünf Reihen — die Tasten wurden
 * dabei 35dp flach und waren nicht mehr zu treffen. Deshalb prüft dieser Test nicht nur die Größe des
 * Rahmens, sondern auch, dass die Tasten quer <b>mindestens so groß wie im Hochformat</b> sind.</p>
 *
 * <p>Geprüft wird das Messen, nicht das Aussehen: Robolectric zeichnet nicht.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class CalcKeyboardSizeTest {

    private CalcKeyboardView tastatur() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_Ausgaben);
        return new CalcKeyboardView(activity);
    }

    /** Misst die Tastatur so, wie ein Bildschirm dieser Größe es täte. */
    private static void miss(View view, int breitePx, int hoehePx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(breitePx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(hoehePx, View.MeasureSpec.AT_MOST));
    }

    private static int bildschirmHoehe(View view) {
        return view.getResources().getDisplayMetrics().heightPixels;
    }

    private static float dichte(View view) {
        return view.getResources().getDisplayMetrics().density;
    }

    // ---- Querformat: der Rahmen ----

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void querIstSieDoppeltSoBreitWieHoch() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));

        assertEquals("Breite soll das Doppelte der Höhe sein",
                2 * kb.getMeasuredHeight(), kb.getMeasuredWidth());
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void querHoechstensDieHalbeHoehe() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));

        assertTrue("höher als die halbe Seite: " + kb.getMeasuredHeight()
                        + " von " + bildschirmHoehe(kb),
                kb.getMeasuredHeight() <= bildschirmHoehe(kb) / 2);
    }

    /** Sie soll Raum neben sich lassen – sonst wäre das Formular daneben nicht zu sehen. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void querLaesstSieRaumNebenSich() {
        CalcKeyboardView kb = tastatur();
        int breite = kb.getResources().getDisplayMetrics().widthPixels;
        miss(kb, breite, bildschirmHoehe(kb));

        assertTrue("nimmt mehr als die halbe Breite ein", kb.getMeasuredWidth() < breite / 2);
    }

    // ---- Querformat: die Tasten ----

    /**
     * Der eigentliche Zweck dieser Anordnung. Beim ersten Anlauf schrumpften die Tasten quer auf 35dp;
     * drei Reihen statt fünf machen sie größer als im Hochformat, nicht kleiner.
     */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void tastenSindQuerGroesserAlsHoch() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));
        kb.layout(0, 0, kb.getMeasuredWidth(), kb.getMeasuredHeight());
        float querDp = kb.findViewById(R.id.key7).getMeasuredHeight() / dichte(kb);

        assertTrue("quer nur " + querDp + "dp hoch, hoch sind es 54dp", querDp >= 54f);
    }

    /** Sechs Spalten, und in ihnen liegen die Tasten so, wie sie liegen sollen. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void dieAnordnungStimmt() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));

        assertEquals("sechs Spalten", 6, kb.getChildCount());
        // Die 0 geht über zwei Reihen, das Komma darüber über eine.
        assertEquals(2 * kb.findViewById(R.id.keyDot).getMeasuredHeight(),
                kb.findViewById(R.id.key0).getMeasuredHeight(), 2);
        // Dasselbe für OK unter dem Zurück-Pfeil.
        assertEquals(2 * kb.findViewById(R.id.keyDel).getMeasuredHeight(),
                kb.findViewById(R.id.keyOk).getMeasuredHeight(), 2);
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void alleTastenSindNochDa() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));

        int tasten = 0;
        for (int c = 0; c < kb.getChildCount(); c++) {
            ViewGroup spalte = (ViewGroup) kb.getChildAt(c);
            for (int k = 0; k < spalte.getChildCount(); k++) {
                assertTrue("Taste ohne Höhe", spalte.getChildAt(k).getMeasuredHeight() > 0);
                tasten++;
            }
        }
        assertEquals(16, tasten);
    }

    // ---- Querformat: in einem Dialog ----

    /**
     * Ein Dialog ist quer deutlich niedriger als der Bildschirm, und ein {@code AlertDialog} scrollt
     * seine Ansicht nicht — die unterste Tastenreihe wurde dort abgeschnitten, und mit ihr die
     * OK-Taste, die den Betrag übernimmt.
     *
     * <p>Der erste Anlauf ließ die Tastatur sich in die Vorgabe des Containers einpassen. Sie passte
     * dann hinein, war aber nicht mehr zu lesen. Also andersherum: die gewohnte Größe behalten und den
     * Dialog scrollbar machen. Genau das ist hier festgehalten — in einem scrollenden Rahmen misst sich
     * die Tastatur <b>unverändert</b>, auch wenn der Rahmen niedriger ist.</p>
     */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void inEinemScrollendenRahmenBleibtSieSoGrossWieSonst() {
        CalcKeyboardView frei = tastatur();
        int breite = frei.getResources().getDisplayMetrics().widthPixels;
        miss(frei, breite, bildschirmHoehe(frei));
        int gewohnteHoehe = frei.getMeasuredHeight();

        CalcKeyboardView kb = tastatur();
        android.widget.LinearLayout inhalt = new android.widget.LinearLayout(kb.getContext());
        inhalt.setOrientation(android.widget.LinearLayout.VERTICAL);
        inhalt.addView(kb);
        View rahmen = AppDialog.scrollable(inhalt);

        int imDialog = bildschirmHoehe(kb) / 4;
        miss(rahmen, breite, imDialog);

        assertEquals("die Tastatur behält ihre Größe", gewohnteHoehe, kb.getMeasuredHeight());
        assertTrue("und der Rahmen bleibt im Dialog: " + rahmen.getMeasuredHeight(),
                rahmen.getMeasuredHeight() <= imDialog);
    }

    /** Was nicht hineinpasst, muss erreichbar bleiben – der Rahmen scrollt. */
    @Test
    @Config(qualifiers = "w915dp-h412dp-land")
    public void derRahmenScrolltUeberDasWasNichtHineinpasst() {
        CalcKeyboardView kb = tastatur();
        android.widget.LinearLayout inhalt = new android.widget.LinearLayout(kb.getContext());
        inhalt.setOrientation(android.widget.LinearLayout.VERTICAL);
        inhalt.addView(kb);
        View rahmen = AppDialog.scrollable(inhalt);
        int breite = kb.getResources().getDisplayMetrics().widthPixels;
        int imDialog = bildschirmHoehe(kb) / 4;

        miss(rahmen, breite, imDialog);
        rahmen.layout(0, 0, rahmen.getMeasuredWidth(), rahmen.getMeasuredHeight());

        assertTrue("kein ScrollView", rahmen instanceof android.widget.ScrollView);
        assertTrue("der Inhalt ist höher als der Rahmen – sonst gäbe es nichts zu scrollen",
                inhalt.getMeasuredHeight() > rahmen.getMeasuredHeight());
    }

    // ---- Hochformat: die Gegenprobe ----

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    public void hochBleibtAllesWieBisher() {
        CalcKeyboardView kb = tastatur();
        int breite = kb.getResources().getDisplayMetrics().widthPixels;
        miss(kb, breite, bildschirmHoehe(kb));

        assertEquals("hoch soll sie die volle Breite füllen", breite, kb.getMeasuredWidth());
        // Fünf Reihen zu 54dp plus je 3dp Rand oben und unten.
        float erwartet = 5 * (54f + 6f) * dichte(kb);
        assertEquals(erwartet, kb.getMeasuredHeight(), 2f);
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    public void hochBleibenEsFuenfReihen() {
        CalcKeyboardView kb = tastatur();
        miss(kb, kb.getResources().getDisplayMetrics().widthPixels, bildschirmHoehe(kb));

        assertEquals(5, kb.getChildCount());
    }
}
