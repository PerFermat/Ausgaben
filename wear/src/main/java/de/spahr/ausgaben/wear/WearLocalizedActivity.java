package de.spahr.ausgaben.wear;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.LayoutInflaterCompat;

/**
 * Basis-Activity der Uhr: hüllt den Context in den {@link WearLocaleWrapper} (Code-Texte) und installiert
 * eine {@link WearViewFactory} (Layout-Texte beim Aufblasen) für die Übersetzung.
 */
public class WearLocalizedActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(WearLocaleWrapper.wrap(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Per-App-Sprache anwenden (nutzt die kompilierten DE/EN-Ressourcen).
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(WearStrings.code(this)));
        LayoutInflater inflater = LayoutInflater.from(this);
        if (inflater.getFactory2() == null) {
            LayoutInflaterCompat.setFactory2(inflater, new WearViewFactory(getDelegate(), inflater));
        }
        super.onCreate(savedInstanceState);
    }
}
