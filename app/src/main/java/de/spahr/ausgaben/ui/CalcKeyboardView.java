package de.spahr.ausgaben.ui;

import android.content.Context;
import android.text.Editable;
import android.util.AttributeSet;
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

    private EditText target;
    private OnOk onOk;
    private boolean allowNegative = false;   // z. B. Bestände-Bewegungen dürfen negativ sein

    public CalcKeyboardView(Context context) {
        super(context);
        init(context);
    }

    public CalcKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_calc_keyboard, this, true);

        int[] valueKeys = {R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
                R.id.keyAdd, R.id.keyMul, R.id.keyMinus};
        for (int id : valueKeys) {
            findViewById(id).setOnClickListener(v -> insert(v.getTag().toString()));
        }

        // Dezimaltaste: Beschriftung und eingefügtes Zeichen aus der Sprache (Komma / Punkt).
        String dec = context.getString(R.string.calc_decimal_key);
        MaterialButton keyDot = findViewById(R.id.keyDot);
        keyDot.setText(dec);
        keyDot.setOnClickListener(v -> insert(dec));

        View keyDel = findViewById(R.id.keyDel);
        keyDel.setOnClickListener(v -> deleteOne());
        keyDel.setOnLongClickListener(v -> {
            clearAll();
            return true;
        });

        findViewById(R.id.keyOk).setOnClickListener(v -> evaluate());
    }

    /** Verbindet die Tastatur mit einem Betragsfeld und unterdrückt dort die System-Tastatur. */
    public void attachTo(EditText field) {
        this.target = field;
        if (field != null) {
            field.setShowSoftInputOnFocus(false);
        }
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
        field.setFilters(new android.text.InputFilter[]{new CalcInputFilter()});
        CalcKeyboardView kb = new CalcKeyboardView(container.getContext());
        kb.setAllowNegative(allowNegative);
        kb.attachTo(field);
        kb.setVisibility(GONE);
        container.addView(kb);
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                kb.attachTo(field);
                kb.setVisibility(VISIBLE);
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
