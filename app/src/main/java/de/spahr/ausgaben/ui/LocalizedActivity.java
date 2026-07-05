package de.spahr.ausgaben.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.LayoutInflaterCompat;

import de.spahr.ausgaben.i18n.I18nViewFactory;
import de.spahr.ausgaben.i18n.LocaleContextWrapper;

/**
 * Basis-Activity aller App-Screens. Hüllt den Context in einen {@link LocaleContextWrapper} (übersetzt
 * Code-Texte) und installiert eine {@link I18nViewFactory} (übersetzt Layout-Texte beim Aufblasen). So
 * erscheinen alle Texte in der gewählten Sprache.
 */
public class LocalizedActivity extends AppCompatActivity {

    private Resources translatedResources;
    private Resources translatedBase;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleContextWrapper.wrap(base));
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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(this);
        // Vor super.onCreate setzen, damit AppCompat keine eigene Factory installiert.
        if (inflater.getFactory2() == null) {
            LayoutInflaterCompat.setFactory2(inflater, new I18nViewFactory(getDelegate(), inflater));
        }
        super.onCreate(savedInstanceState);
    }
}
