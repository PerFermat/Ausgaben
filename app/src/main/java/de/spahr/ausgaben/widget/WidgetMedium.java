package de.spahr.ausgaben.widget;

import android.content.Context;
import android.widget.RemoteViews;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.ui.MainActivity;

/** Mittleres Widget (4×2): Saldo + drei Schnellaktionen (Buchung, Sprache, Betrag). */
public class WidgetMedium extends AusgabenWidget {
    @Override
    protected int layoutId() {
        return R.layout.widget_medium;
    }

    @Override
    protected void bindExtra(Context ctx, RemoteViews v, WidgetData d) {
        v.setOnClickPendingIntent(R.id.widgetActionBook, openMain(ctx, MainActivity.WIDGET_ACTION_NEW, 11));
        v.setOnClickPendingIntent(R.id.widgetActionVoice, openMain(ctx, MainActivity.WIDGET_ACTION_VOICE, 12));
        v.setOnClickPendingIntent(R.id.widgetActionAmount, openMain(ctx, MainActivity.WIDGET_ACTION_DIGITS, 13));
    }
}
