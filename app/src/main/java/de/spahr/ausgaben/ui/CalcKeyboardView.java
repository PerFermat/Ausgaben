package de.spahr.ausgaben.ui;

import android.content.Context;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.settings.AmountExpression;
import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Eigene Rechentastatur für die Betragseingabe (ersetzt die System-Tastatur). Layout:
 * <pre>
 *   7 8 9 ⌫
 *   4 5 6 *
 *   1 2 3 +
 *   0(breit) , OK
 * </pre>
 * Die Tasten schreiben in ein zugewiesenes {@link EditText}; erlaubte Zeichen/Struktur setzt der
 * {@link CalcInputFilter} am Feld durch (ungültige Tasten bleiben so wirkungslos). {@code ⌫} löscht das
 * letzte Zeichen (langer Druck leert das Feld), {@code OK} wertet die Rechnung aus (nur {@code + *}) und
 * ersetzt den Feldinhalt durch das Ergebnis. Die Tasten fangen keinen Fokus, damit das Feld aktiv bleibt.
 */
public class CalcKeyboardView extends LinearLayout {

    /** Rückmeldung der OK-Taste: {@code valid} = Rechnung war gültig und wurde übernommen. */
    public interface OnOk {
        void onOk(boolean valid);
    }

    /** Seitenverhältnis im Querformat: doppelt so breit wie hoch (drei Reihen, sechs Spalten). */
    private static final int LAND_ASPECT = 2;

    private EditText target;
    private OnOk onOk;
    private boolean allowNegative = false;   // z. B. Bestände-Bewegungen dürfen negativ sein

    /** Der Platzhalter, der im Hochformat die Höhe der Tastatur freihält; {@code null} = keiner. */
    private View spacer;

    public CalcKeyboardView(Context context) {
        super(context);
        init(context);
    }

    public CalcKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Quer sechs Spalten nebeneinander, hoch fünf Reihen untereinander. Beide Fassungen tragen
        // dieselben Tasten-IDs; die Verdrahtung darunter kennt den Unterschied nicht.
        boolean land = isLandscape();
        setOrientation(land ? HORIZONTAL : VERTICAL);
        setClipChildren(false);   // sonst kappt die Tastatur den Elevation-Schatten der Tasten an ihrem Rand
        LayoutInflater.from(context).inflate(
                land ? R.layout.view_calc_keyboard_land : R.layout.view_calc_keyboard, this, true);

        int[] valueKeys = {R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
                R.id.keyAdd, R.id.keyMul, R.id.keyMinus};
        for (int id : valueKeys) {
            findViewById(id).setOnClickListener(withHaptic(v -> insert(v.getTag().toString())));
        }

        // Dezimaltaste: Beschriftung und eingefügtes Zeichen aus den Einstellungen (Komma / Punkt) –
        // nicht aus der Sprache, sonst schriebe die Taste ein Zeichen, das das Feld nicht annimmt.
        String dec = String.valueOf(MoneyFormat.decimalSeparator());
        MaterialButton keyDot = findViewById(R.id.keyDot);
        keyDot.setText(dec);
        keyDot.setOnClickListener(withHaptic(v -> insert(dec)));

        View keyDel = findViewById(R.id.keyDel);
        keyDel.setOnClickListener(withHaptic(v -> deleteOne()));
        keyDel.setOnLongClickListener(v -> {
            clearAll();
            return true;
        });

        findViewById(R.id.keyOk).setOnClickListener(withHaptic(v -> evaluate()));
    }

    /** Legt die haptische Rückmeldung eines Tastendrucks über den eigentlichen Klick-Effekt. */
    private static View.OnClickListener withHaptic(View.OnClickListener inner) {
        return v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            inner.onClick(v);
        };
    }

    /**
     * Im Querformat höchstens halbe Bildschirmhöhe, und doppelt so breit wie hoch.
     *
     * <p>Quer bedeckte die Tastatur in der Anordnung des Hochformats fast die ganze Seite. Sie wird
     * deshalb zu einem flachen Feld unten in der Mitte: darüber bleibt das Formular sichtbar, links und
     * rechts daneben ebenfalls, und dort lässt es sich bedienen.</p>
     *
     * <p>Die Aufteilung der Fläche auf die Tasten macht das Layout selbst über Gewichte — hier wird nur
     * der Rahmen gesetzt. Auch die Zentrierung rechnet diese Klasse nicht: der Eltern-Container stellt
     * das Kind nach dessen <b>gemessener</b> Breite und der {@code layout_gravity} auf.</p>
     *
     * <p>Die Höhenvorgabe des Containers wird dabei <b>bewusst übergangen</b>. Ein Versuch, sich in
     * einen quer niedrigen Dialog einzupassen, machte die Tasten so flach, dass die Ziffern nicht mehr
     * zu lesen waren — eine Tastatur, die hineinpasst, aber nicht mehr zu bedienen ist, hilft niemandem.
     * Wer sie in einen knappen Container setzt, macht diesen scrollbar; siehe
     * {@link AppDialog#scrollable(View)}.</p>
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (!isLandscape()) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        int height = Math.min(getResources().getDisplayMetrics().heightPixels / 2,
                MeasureSpec.getSize(widthSpec) / LAND_ASPECT);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(height * LAND_ASPECT, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Merkt sich den Platzhalter, der im Hochformat die Höhe der Tastatur freihält.
     *
     * <p>Dort schwebt die Tastatur nicht, sondern das Formular endet über ihr — wie bisher. Der
     * Platzhalter sitzt im scrollenden Teil und wächst genau dann, wenn die Tastatur sichtbar ist. So
     * bleiben alle vorhandenen {@code setVisibility}-Aufrufe unverändert.</p>
     */
    public void reserveSpaceWith(View placeholder) {
        this.spacer = placeholder;
        updateSpacer();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateSpacer();
    }

    /**
     * Zieht den Platzhalter nach. Seine Höhe ist die der Tastatur — ein {@code Space} mit
     * {@code wrap_content} wäre null hoch und hielte gar nichts frei.
     */
    private void updateSpacer() {
        if (spacer == null) {
            return;
        }
        // Quer schwebt die Tastatur über dem Formular – dann ist nichts freizuhalten.
        boolean brauchtPlatz = getVisibility() == VISIBLE && !isLandscape();
        if (brauchtPlatz && spacer.getLayoutParams().height != getMeasuredHeight()) {
            spacer.getLayoutParams().height = getMeasuredHeight();
            spacer.requestLayout();
        }
        spacer.setVisibility(brauchtPlatz ? VISIBLE : GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        // Erst jetzt steht die Höhe fest; beim Einblenden war sie noch unbekannt.
        updateSpacer();
    }

    /** Verbindet die Tastatur mit einem Betragsfeld und unterdrückt dort die System-Tastatur. */
    public void attachTo(EditText field) {
        this.target = field;
        if (field != null) {
            field.setShowSoftInputOnFocus(false);
            // Markierung, an der LocalizedActivity dieses Feld wiedererkennt — es gibt keinen
            // öffentlichen Getter zu setShowSoftInputOnFocus(boolean), an dem sich das sonst ablesen ließe.
            field.setTag(R.id.calcKeyboardBound, Boolean.TRUE);
        }
    }

    /** Ist {@code field} an eine eigene Rechentastatur gebunden (System-Tastatur dafür unterdrückt)? */
    public static boolean isBoundField(View field) {
        return field != null && Boolean.TRUE.equals(field.getTag(R.id.calcKeyboardBound));
    }

    /**
     * Blendet die System-Tastatur für dieses Feld aus. Nötig beim Fokuswechsel von einem normalen Textfeld
     * (System-Tastatur offen) auf ein Betragsfeld: {@code setShowSoftInputOnFocus(false)} verhindert nur das
     * erneute Öffnen, schließt aber die bereits offene Tastatur nicht. Per {@code post()} nach dem
     * Fokuswechsel, damit ein noch anstehendes „Anzeigen" der IME sicher überschrieben wird.
     */
    public static void hideSystemKeyboard(final View field) {
        field.post(() -> Keyboard.hide(field));
    }

    public void setOnOk(OnOk listener) {
        this.onOk = listener;
    }

    /** Erlaubt negative Ergebnisse (z. B. für vorzeichenbehaftete Bestände-Bewegungen). */
    public void setAllowNegative(boolean allow) {
        this.allowNegative = allow;
    }

    /**
     * Bindet die eigene Tastatur an ein Betrags-Feld in einem Dialog: Eingabefilter setzen, System-Tastatur
     * für dieses Feld unterdrücken, Tastatur unten in {@code container} einhängen und <b>nur bei Fokus</b> des
     * Feldes einblenden (andere Felder – z. B. eine Notiz – behalten so ihre System-Tastatur). Beim Verlassen
     * bzw. „OK" wird die Rechnung ausgewertet und durch das Ergebnis ersetzt.
     */
    public static CalcKeyboardView installToggling(EditText field, LinearLayout container,
                                                   boolean allowNegative) {
        AmountField.prepareCalc(field);
        CalcKeyboardView kb = new CalcKeyboardView(container.getContext());
        kb.setAllowNegative(allowNegative);
        kb.attachTo(field);
        kb.setVisibility(GONE);
        container.addView(kb);
        // Im Dialog sitzt die Tastatur in einem senkrechten LinearLayout; ohne diese Angabe klebte das
        // quadratische Tastenfeld quer am linken Rand.
        if (kb.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) kb.getLayoutParams()).gravity =
                    android.view.Gravity.CENTER_HORIZONTAL;
        }
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                kb.attachTo(field);
                kb.setVisibility(VISIBLE);
                hideSystemKeyboard(field);   // ggf. noch offene System-Tastatur des Vorfelds schließen
            } else {
                kb.setVisibility(GONE);
                kb.evaluateAndReplace();
            }
        });
        kb.setOnOk(valid -> {
            if (valid) {
                field.clearFocus();
            }
        });
        return kb;
    }

    private void insert(String ch) {
        if (target == null) {
            return;
        }
        Editable e = target.getText();
        int st = target.getSelectionStart();
        int en = target.getSelectionEnd();
        if (st < 0) {
            st = e.length();
            en = e.length();
        }
        e.replace(Math.min(st, en), Math.max(st, en), ch);   // CalcInputFilter lehnt Ungültiges ab
    }

    private void deleteOne() {
        if (target == null) {
            return;
        }
        Editable e = target.getText();
        int st = target.getSelectionStart();
        int en = target.getSelectionEnd();
        if (st < 0) {
            return;
        }
        if (st != en) {
            e.delete(Math.min(st, en), Math.max(st, en));
        } else if (st > 0) {
            e.delete(st - 1, st);
        }
    }

    private void clearAll() {
        if (target != null) {
            target.setText("");
        }
    }

    private void evaluate() {
        boolean ok = evaluateAndReplace();
        if (onOk != null) {
            onOk.onOk(ok);
        }
    }

    /**
     * Wertet die Rechnung aus und ersetzt den Feldinhalt durch das Ergebnis. Leeres Feld gilt als gültig
     * (nichts zu tun). Gibt {@code false} bei ungültiger Rechnung (oder unerlaubt negativem Ergebnis) zurück.
     */
    boolean evaluateAndReplace() {
        if (target == null) {
            return false;
        }
        String raw = target.getText() == null ? "" : target.getText().toString().trim();
        if (raw.isEmpty()) {
            return true;
        }
        Long cents = AmountExpression.toCents(raw);
        if (cents == null || (!allowNegative && cents < 0)) {
            return false;
        }
        String result = MoneyFormat.plain(cents);
        if (!result.equals(raw)) {
            target.setText(result);
            target.setSelection(result.length());
        }
        return true;
    }
}
