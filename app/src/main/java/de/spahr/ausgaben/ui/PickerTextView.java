package de.spahr.ausgaben.ui;

import android.content.Context;
import android.util.AttributeSet;

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
    }

    public PickerTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ohneAutofill();
    }

    public PickerTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ohneAutofill();
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

    @Override
    public boolean enoughToFilter() {
        return true;
    }
}
