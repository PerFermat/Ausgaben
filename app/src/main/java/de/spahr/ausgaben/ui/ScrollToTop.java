package de.spahr.ausgaben.ui;

import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Der Weg zurück an den Listenanfang („nach oben“-Knopf).
 *
 * <p>{@link RecyclerView#smoothScrollToPosition(int)} rollt mit fester Geschwindigkeit – bei ein paar
 * hundert Buchungen dauert das viele Sekunden und die App baut dabei jede übersprungene Zeile. Hier
 * ist es umgekehrt: die Dauer steht fest, die Geschwindigkeit ergibt sich daraus.</p>
 *
 * <ul>
 *   <li>Ein kurzes Stück rollt kurz, ein langes höchstens {@link #MAX_MS} – nie länger.</li>
 *   <li>Ist der Anfang weiter als {@link #SPRUNG_AB} Zeilen weg, springt die Liste unsichtbar auf
 *       diese Zeile und rollt erst von dort. Sichtbar ist ohnehin nur der letzte Bildschirm, und die
 *       Zeilen dazwischen müssen gar nicht erst entstehen.</li>
 * </ul>
 */
final class ScrollToTop {

    /** Höchstdauer der Animation. */
    static final int MAX_MS = 1000;

    /** Untergrenze: darunter wirkt es wie ein Sprung, nicht wie eine Bewegung. */
    static final int MIN_MS = 150;

    /** Dauer für eine Bildschirmhöhe Weg; längere Wege dauern anteilig länger, bis {@link #MAX_MS}. */
    static final int MS_JE_BILDSCHIRM = 300;

    /** Ab dieser Entfernung in Zeilen wird gesprungen statt gerollt. */
    static final int SPRUNG_AB = 30;

    private ScrollToTop() {
    }

    /**
     * Die Dauer für einen Weg von {@code streckePx} bei einer sichtbaren Höhe von {@code hoehePx}.
     * Proportional zur Strecke, gedeckelt auf {@link #MAX_MS} und mindestens {@link #MIN_MS}.
     */
    static int dauerMs(int streckePx, int hoehePx) {
        if (streckePx <= 0 || hoehePx <= 0) {
            return MIN_MS;
        }
        long ms = (long) MS_JE_BILDSCHIRM * streckePx / hoehePx;
        return (int) Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    /** Rollt die Liste an den Anfang – gesprungen wird nur, was ohnehin niemand sieht. */
    static void rolle(@NonNull RecyclerView liste) {
        LinearLayoutManager lm = (LinearLayoutManager) liste.getLayoutManager();
        if (lm == null) {
            return;
        }
        int erste = lm.findFirstVisibleItemPosition();
        if (erste == RecyclerView.NO_POSITION || erste == 0) {
            liste.scrollToPosition(0);
            return;
        }
        if (erste > SPRUNG_AB) {
            // Der Sprung bleibt unsichtbar: gerollt wird danach über die volle Bildschirmhöhe.
            lm.scrollToPositionWithOffset(SPRUNG_AB, 0);
            liste.post(() -> rolle(liste));
            return;
        }
        int strecke = strecke(lm, erste);
        int hoehe = Math.max(1, liste.getHeight());
        starte(lm, liste, dauerMs(strecke, hoehe) / (float) Math.max(1, strecke));
    }

    /** Geschätzter Weg bis zum Anfang: die Zeilen darüber in der Höhe der obersten sichtbaren. */
    private static int strecke(LinearLayoutManager lm, int erste) {
        View oben = lm.findViewByPosition(erste);
        if (oben == null) {
            return 0;
        }
        int zeilenhoehe = Math.max(1, oben.getHeight());
        return erste * zeilenhoehe - Math.min(0, oben.getTop());
    }

    private static void starte(LinearLayoutManager lm, RecyclerView liste, float msJePixel) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(liste.getContext()) {
            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics anzeige) {
                return msJePixel;
            }
        };
        scroller.setTargetPosition(0);
        lm.startSmoothScroll(scroller);
    }
}
