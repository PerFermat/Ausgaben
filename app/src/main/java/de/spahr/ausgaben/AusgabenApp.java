package de.spahr.ausgaben;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import de.spahr.ausgaben.settings.SettingsStore;
import de.spahr.ausgaben.ui.LockActivity;

/**
 * Application-Einstieg. Setzt den Nacht-Modus und steuert – bei aktiver Einstellung – die optionale
 * biometrische App-Sperre.
 *
 * <p>Über {@link ActivityLifecycleCallbacks} wird der Wechsel in den Hintergrund erkannt (Zähler
 * gestarteter Activities). Kehrt die App in den Vordergrund zurück (oder startet kalt) und ist die Sperre
 * aktiv und noch nicht entsperrt, wird eine {@link LockActivity} über die aktuelle Ansicht gelegt. Ein
 * Konfigurationswechsel (Rotation) löst dank {@link Activity#isChangingConfigurations()} keine erneute
 * Sperre aus.</p>
 */
public class AusgabenApp extends Application implements Application.ActivityLifecycleCallbacks {

    private SettingsStore settings;
    private int startedActivities;
    /** false = App muss (wieder) entsperrt werden. Startet gesperrt (Kaltstart erzwingt Auth). */
    private boolean unlocked = false;
    /** true, solange die LockActivity bereits angezeigt wird (verhindert Mehrfach-Start). */
    private boolean lockShowing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new SettingsStore(this);
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode());
        registerActivityLifecycleCallbacks(this);
    }

    /** Markiert die App als entsperrt (nach erfolgreicher Auth bzw. bei ausgeschalteter Sperre). */
    public void markUnlocked() {
        unlocked = true;
        lockShowing = false;
    }

    private boolean lockRequired() {
        return settings.isAppLockEnabled() && !unlocked;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof LockActivity) {
            return; // der Sperrbildschirm selbst darf laufen
        }
        if (lockRequired() && !lockShowing) {
            lockShowing = true;
            Intent intent = new Intent(activity, LockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities--;
        // Alle Activities gestoppt und kein reiner Konfigurationswechsel → App ist im Hintergrund → sperren.
        if (startedActivities <= 0 && !activity.isChangingConfigurations()) {
            startedActivities = 0;
            unlocked = false;
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
