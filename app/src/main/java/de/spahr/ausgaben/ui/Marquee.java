package de.spahr.ausgaben.ui;

import android.text.TextUtils;
import android.widget.TextView;

/**
 * Macht aus einem einzeiligen {@link TextView} eine Laufschrift, falls der Text nicht in die Breite passt.
 * Nötig, weil bei großer Schriftgröße einzeilige Felder (Kontoname, Saldo, Listen-Titel) sonst
 * abgeschnitten würden. Mehrzeilige/umbrechende Felder werden bewusst nicht angefasst.
 *
 * <p>Der {@code selected}-Zustand lässt die Laufschrift ohne Fokus dauerhaft laufen.</p>
 */
public final class Marquee {

    private Marquee() {
    }

    /** Aktiviert dauerhafte Laufschrift für einen einzeiligen TextView (passt der Text, bleibt er statisch). */
    public static void apply(TextView tv) {
        if (tv == null) {
            return;
        }
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tv.setMarqueeRepeatLimit(-1);
        tv.setHorizontallyScrolling(true);
        tv.setSelected(true);
    }
}
