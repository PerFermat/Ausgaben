package de.spahr.ausgaben.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.ui.VoiceCaptureActivity;

/**
 * Typ-Widget (wie die Uhr): Einnahme/Umbuchung/Ausgabe als Symbol-Knöpfe + Standardort-Saldo. Ein Knopf
 * startet sofort die Spracherkennung für den jeweiligen Typ (ohne die App sichtbar zu öffnen). Der graue
 * Wechsel-Knopf schaltet das gewählte Konto bzw. dessen Orte durch (Saldo + Ziel der neuen Buchung).
 */
public class WidgetType extends AusgabenWidget {

    static final String ACTION_CYCLE = "de.spahr.ausgaben.widget.CYCLE_SELECTION";
    static final String ACTION_RESET = "de.spahr.ausgaben.widget.RESET_SELECTION";

    @Override
    protected int layoutId() {
        return R.layout.widget_type;
    }

    @Override
    protected String[] selection(Context app) {
        return WidgetSelection.current(app);
    }

    @Override
    protected void bindExtra(Context ctx, RemoteViews v, WidgetData d) {
        String[] sel = WidgetSelection.current(ctx); // [account, place]
        v.setOnClickPendingIntent(R.id.widgetTypeIncome, voice(ctx, Repository.VOICE_TYPE_INCOME, sel, 15));
        v.setOnClickPendingIntent(R.id.widgetTypeTransfer, voice(ctx, Repository.VOICE_TYPE_TRANSFER, sel, 16));
        v.setOnClickPendingIntent(R.id.widgetTypeExpense, voice(ctx, Repository.VOICE_TYPE_EXPENSE, sel, 17));
        v.setOnClickPendingIntent(R.id.widgetTypeSwitch, cycle(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_CYCLE.equals(action)) {
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                WidgetSelection.advance(app);
                scheduleReset(app);
                redraw(app);
            }).start();
            return;
        }
        if (ACTION_RESET.equals(action)) {
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                WidgetSelection.reset(app);
                redraw(app);
            }).start();
            return;
        }
        super.onReceive(ctx, intent);
    }

    private void redraw(Context app) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        onUpdate(app, mgr, mgr.getAppWidgetIds(new ComponentName(app, WidgetType.class)));
    }

    /** Plant den automatischen Rücksprung auf Standardkonto/-ort nach dem Timeout (ersetzt einen alten Alarm). */
    private static void scheduleReset(Context ctx) {
        android.app.AlarmManager am =
                (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent i = new Intent(ctx, WidgetType.class).setAction(ACTION_RESET);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 19, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.set(android.app.AlarmManager.RTC,
                System.currentTimeMillis() + WidgetSelection.TIMEOUT_MS, pi);
    }

    /** Startet die unsichtbare Sprach-Erfassung für den Typ auf dem gewählten Konto/Ort. */
    private static PendingIntent voice(Context ctx, String type, String[] sel, int requestCode) {
        Intent i = new Intent(ctx, VoiceCaptureActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(VoiceCaptureActivity.EXTRA_TYPE, type);
        if (sel != null && sel[0] != null && !sel[0].isEmpty()) {
            i.putExtra(VoiceCaptureActivity.EXTRA_ACCOUNT, sel[0]);
            i.putExtra(VoiceCaptureActivity.EXTRA_PLACE, sel.length > 1 && sel[1] != null ? sel[1] : "");
        }
        return PendingIntent.getActivity(ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Schaltet die Konto/Ort-Auswahl weiter (Broadcast an dieses Widget). */
    private static PendingIntent cycle(Context ctx) {
        Intent i = new Intent(ctx, WidgetType.class).setAction(ACTION_CYCLE);
        return PendingIntent.getBroadcast(ctx, 18, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
