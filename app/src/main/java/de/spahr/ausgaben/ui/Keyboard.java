package de.spahr.ausgaben.ui;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Die System-Tastatur schließen – die eine Stelle, an der das steht. Vorher lagen dieselben vier
 * Zeilen zweimal im Baum, und eine dritte Abschrift wäre dazugekommen, als die Vorschlagsfelder ihre
 * Fertig-Taste bekamen.
 */
final class Keyboard {

    private Keyboard() {
    }

    /**
     * Schließt die Tastatur, die zum Fenster von {@code anchor} gehört. Der Fokus bleibt, wo er ist –
     * wer ihn abgeben will, muß das selbst tun.
     *
     * <p>Hängt die Ansicht (noch) an keinem Fenster, gibt es auch keine Tastatur zu schließen.</p>
     */
    static void hide(View anchor) {
        if (anchor == null || anchor.getWindowToken() == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager)
                anchor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(anchor.getWindowToken(), 0);
        }
    }
}
