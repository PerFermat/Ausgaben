package de.spahr.ausgaben.widget;

import de.spahr.ausgaben.R;

/** Kleines Widget (2×1): nur der Saldo des Standardorts. */
public class WidgetSmall extends AusgabenWidget {
    @Override
    protected int layoutId() {
        return R.layout.widget_small;
    }
}
