package de.spahr.ausgaben.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.settings.Currencies;
import de.spahr.ausgaben.settings.MoneyFormat;
import de.spahr.ausgaben.settings.PlacesStore;
import de.spahr.ausgaben.settings.SettingsStore;
import de.spahr.ausgaben.ui.MainActivity;

/**
 * Basis für die drei Homescreen-Widgets (klein/mittel/groß). Zeigt den Saldo des Standardorts
 * (Standardkonto → Standardort) wie das Uhr-Widget; Unterklassen ergänzen Aktionen bzw. die Buchungsliste.
 */
public abstract class AusgabenWidget extends AppWidgetProvider {

    protected abstract int layoutId();

    /** Unterklassen-spezifische Bindung (Aktionsknöpfe, Liste …). */
    protected void bindExtra(Context ctx, RemoteViews v, WidgetData d) {
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            WidgetData d = WidgetData.load(app);
            for (int id : ids) {
                RemoteViews v = new RemoteViews(app.getPackageName(), layoutId());
                bindCommon(app, v, d);
                bindExtra(app, v, d);
                try {
                    mgr.updateAppWidget(id, v);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void bindCommon(Context ctx, RemoteViews v, WidgetData d) {
        v.setTextViewText(R.id.widgetPlace,
                d.place.isEmpty() ? ctx.getString(R.string.app_name) : d.place);
        v.setTextViewText(R.id.widgetBalance, d.balance);
        v.setTextColor(R.id.widgetBalance, ContextCompat.getColor(ctx,
                d.negative ? R.color.expense_red : R.color.widget_text));
        v.setOnClickPendingIntent(R.id.widgetRoot, openMain(ctx, null, 10));
    }

    /** PendingIntent, der MainActivity öffnet und optional eine Widget-Aktion mitgibt. */
    static PendingIntent openMain(Context ctx, String action, int requestCode) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (action != null) {
            i.putExtra(MainActivity.EXTRA_WIDGET_ACTION, action);
        }
        return PendingIntent.getActivity(ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Alle vorhandenen Ausgaben-Widgets neu zeichnen (z. B. nach einer Buchungsänderung). */
    public static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        Class<?>[] classes = {WidgetSmall.class, WidgetMedium.class, WidgetLarge.class, WidgetType.class};
        for (Class<?> cls : classes) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, cls));
            if (ids != null && ids.length > 0) {
                Intent i = new Intent(ctx, cls);
                i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                ctx.sendBroadcast(i);
            }
        }
    }

    /** Vom Widget angezeigte Daten (im Hintergrund geladen). */
    static class WidgetData {
        String place = "";
        String balance = "";
        boolean negative = false;
        List<Booking> recent = new ArrayList<>();

        static WidgetData load(Context app) {
            MoneyFormat.refresh(app);
            WidgetData d = new WidgetData();
            try {
                String account = new SettingsStore(app).getDefaultAccount();
                String place = account.isEmpty() ? "" : new PlacesStore(app).getDefaultPlace(account);
                if (!account.isEmpty() && place != null && !place.trim().isEmpty()) {
                    place = place.trim();
                    long cents = AppDatabase.getInstance(app).placeEntryDao().getBalance(account, place);
                    d.place = place;
                    d.balance = MoneyFormat.display(cents, Currencies.forAccount(account));
                    d.negative = cents < 0;
                } else {
                    d.balance = app.getString(R.string.widget_balance_empty);
                }
                d.recent = AppDatabase.getInstance(app).bookingDao().getRecent(3);
            } catch (Exception ignored) {
                if (d.balance.isEmpty()) {
                    d.balance = app.getString(R.string.widget_balance_empty);
                }
            }
            return d;
        }
    }
}
