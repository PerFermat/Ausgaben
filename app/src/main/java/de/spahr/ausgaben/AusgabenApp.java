package de.spahr.ausgaben;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import de.spahr.ausgaben.settings.SettingsStore;

public class AusgabenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(new SettingsStore(this).getNightMode());
    }
}
