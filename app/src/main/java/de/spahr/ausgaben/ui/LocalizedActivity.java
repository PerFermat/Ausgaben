package de.spahr.ausgaben.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.LayoutInflaterCompat;

import de.spahr.ausgaben.AusgabenApp;
import de.spahr.ausgaben.i18n.I18nViewFactory;
import de.spahr.ausgaben.i18n.LocaleContextWrapper;
import de.spahr.ausgaben.security.AppLockGate;

/**
 * Basis-Activity aller App-Screens. Hüllt den Context in einen {@link LocaleContextWrapper} (übersetzt
 * Code-Texte) und installiert eine {@link I18nViewFactory} (übersetzt Layout-Texte beim Aufblasen). So
 * erscheinen alle Texte in der gewählten Sprache.
 *
 * <p>Außerdem die eine Stelle, an der jeder Absprung in eine fremde App auffällt: sowohl
 * {@code startActivity} als auch die {@code ActivityResultLauncher} (Kamera, Galerie, Dateiauswahl,
 * Spracherkennung) laufen am Ende durch die hier überschriebenen Methoden. Führt der Intent aus der App
 * heraus, erfährt {@link AusgabenApp} davon und setzt die Sperre nicht sofort scharf.</p>
 */
public class LocalizedActivity extends AppCompatActivity {

    private Resources translatedResources;
    private Resources translatedBase;
    /** Schriftgrößen-Faktor, mit dem diese Activity aufgebaut wurde (für Live-Neuaufbau bei Änderung). */
    private float appliedFontScale = 1f;

    @Override
    protected void attachBaseContext(Context base) {
        // Globale Schriftgröße anwenden: fontScale multiplikativ (stapelt auf die System-Schriftgröße,
        // „normal" = Faktor 1,0 = heutiges Verhalten). Alle sp-Texte skalieren dadurch automatisch.
        appliedFontScale = de.spahr.ausgaben.settings.FontScale.factor();
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.fontScale *= appliedFontScale;
        Context scaled = base.createConfigurationContext(cfg);
        super.attachBaseContext(LocaleContextWrapper.wrap(scaled));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Wurde die Schriftgröße in den Einstellungen geändert, zieht jede Ansicht beim Anzeigen nach.
        if (appliedFontScale != de.spahr.ausgaben.settings.FontScale.factor()) {
            recreate();
        }
    }

    /**
     * Übersetzte {@link Resources} auch für die Activity selbst – so ketten Material-getönte Contexts
     * (Dialog-Buttons, {@code MaterialButton.setText(resId)}) und die Menü-Inflation an die
     * Übersetzungstabelle. Bei Konfigurationswechsel (neue Basis-Resources) wird neu umhüllt.
     */
    @Override
    public Resources getResources() {
        Resources base = super.getResources();
        if (translatedResources == null || translatedBase != base) {
            translatedBase = base;
            translatedResources = LocaleContextWrapper.translate(base);
        }
        return translatedResources;
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        noteHandoff(intent);
        super.startActivity(intent, options);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        noteHandoff(intent);
        super.startActivityForResult(intent, requestCode, options);
    }

    /** Verlässt der Absprung die eigene App, merkt sich die Application die Übergabe. */
    private void noteHandoff(Intent intent) {
        ComponentName target = intent.getComponent();
        if (AppLockGate.leavesApp(getPackageName(), target == null ? null : target.getPackageName())
                && getApplication() instanceof AusgabenApp) {
            ((AusgabenApp) getApplication()).noteExternalHandoff();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(this);
        // Vor super.onCreate setzen, damit AppCompat keine eigene Factory installiert.
        if (inflater.getFactory2() == null) {
            LayoutInflaterCompat.setFactory2(inflater, new I18nViewFactory(getDelegate(), inflater));
        }
        super.onCreate(savedInstanceState);
    }
}
