package de.spahr.ausgaben.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.ui.VoiceCaptureActivity;

/**
 * Typ-Widget (wie die Uhr): Einnahme/Umbuchung/Ausgabe-Knöpfe + Standardort-Saldo. Ein Knopf startet
 * sofort die Spracherkennung für den jeweiligen Typ (ohne die App sichtbar zu öffnen).
 */
public class WidgetType extends AusgabenWidget {
    @Override
    protected int layoutId() {
        return R.layout.widget_type;
    }

    @Override
    protected void bindExtra(Context ctx, RemoteViews v, WidgetData d) {
        v.setOnClickPendingIntent(R.id.widgetTypeIncome, voice(ctx, Repository.VOICE_TYPE_INCOME, 15));
        v.setOnClickPendingIntent(R.id.widgetTypeTransfer, voice(ctx, Repository.VOICE_TYPE_TRANSFER, 16));
        v.setOnClickPendingIntent(R.id.widgetTypeExpense, voice(ctx, Repository.VOICE_TYPE_EXPENSE, 17));
    }

    /** Startet die unsichtbare Sprach-Erfassung für den Typ (nur der System-Sprachdialog erscheint). */
    private static PendingIntent voice(Context ctx, String type, int requestCode) {
        Intent i = new Intent(ctx, VoiceCaptureActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(VoiceCaptureActivity.EXTRA_TYPE, type);
        return PendingIntent.getActivity(ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
