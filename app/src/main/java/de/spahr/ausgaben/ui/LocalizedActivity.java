package de.spahr.ausgaben.ui;

import android.content.Context;
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

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleContextWrapper.wrap(base));
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
