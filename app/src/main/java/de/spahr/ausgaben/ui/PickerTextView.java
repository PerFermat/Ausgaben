package de.spahr.ausgaben.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

/**
 * Das Eingabefeld der Vorschlagslisten. Es unterscheidet sich von Material nur in einem Punkt, an den
 * man von außen nicht herankommt: <b>auch ein leeres Feld sucht</b>.
 *
 * <p>{@code AutoCompleteTextView} fragt vor jedem Auf- und Zuklappen {@link #enoughToFilter()}, also ob
 * der Text mindestens so lang ist wie die eingestellte Schwelle, und schiebt diese Frage nach jeder
 * Meldung des Adapters noch einmal nach. Beim Hineingehen ist unser Feld aber leer – der bisherige
 * Eintrag liegt beiseite, damit man lostippen kann. Die Antwort wäre also „nein", und die gerade
 * geöffnete Liste klappte sofort wieder zu.</p>
 *
 * <p>Über die Schwelle ist dagegen nicht heranzukommen: {@code setThreshold(0)} hebt
 * {@code AutoCompleteTextView} stillschweigend wieder auf 1 an. Bleibt, die Frage selbst zu
 * beantworten.</p>
 */
public class PickerTextView extends MaterialAutoCompleteTextView {

    public PickerTextView(Context context) {
        super(context);
        ohneAutofill();
        mitFertigTaste();
    }

    public PickerTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ohneAutofill();
        mitFertigTaste();
    }

    public PickerTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ohneAutofill();
        mitFertigTaste();
    }

    /**
     * Kein Autofill. Der Passwortdienst des Systems schiebt sonst sein eigenes Fenster über das Feld,
     * nimmt der App den Fensterfokus und unsere Liste klappt sofort wieder zu – zu sehen war das in den
     * Einstellungen, wo das App-Passwort steht: der Dienst hält die ganze Seite für ein Anmeldeformular
     * und bietet sich auf jedem Textfeld an. Ein Vorschlagsfeld hat seine Liste selbst und braucht
     * keinen fremden Vorschlag.
     */
    private void ohneAutofill() {
        setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    }

    /**
     * Unten rechts steht in jedem Vorschlagsfeld dieselbe Fertig-Taste – was sie tut, entscheidet
     * {@link PickerBehaviour}. Ohne diese Zeile sucht sich Android die Taste je nach Umgebung selbst
     * aus, und sie bedeutet mal dies, mal jenes.
     *
     * <p>{@code IME_FLAG_NO_EXTRACT_UI}: quer am Handy zieht die Tastatur sonst ein eigenes
     * Vollbild-Eingabefeld über die Seite – und mit ihm ist die Vorschlagsliste verdeckt, also gerade
     * das, worum es hier geht.</p>
     *
     * <p>Hier und nicht im Layout, damit es für jedes Vorschlagsfeld gilt, auch für die, die später
     * dazukommen. Ein {@code android:imeOptions} im XML bliebe dafür wirkungslos: der Konstruktor läuft
     * nach dem Einlesen der Merkmale.</p>
     */
    private void mitFertigTaste() {
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    @Override
    public boolean enoughToFilter() {
        return true;
    }
}
