package de.spahr.ausgaben.i18n;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Übersetzt beim Aufblasen jedes Views die Text-Attribute ({@code android:text}, {@code hint}, Toolbar-
 * Titel, {@code contentDescription}), die auf einen {@code @string}-Schlüssel verweisen, anhand des aktiven
 * {@link Strings}-Katalogs. Notwendig, weil {@code @string/…} in Layouts direkt aus dem kompilierten
 * String-Pool gelesen wird und die {@code Resources}-Überschreibung umgeht. Code-Texte
 * ({@code getString(...)}) laufen weiterhin über die {@code Resources}-Überschreibung.
 */
public class I18nViewFactory implements LayoutInflater.Factory2 {

    private static final Class<?>[] CTOR_SIG = {Context.class, AttributeSet.class};
    private static final ConcurrentHashMap<String, Constructor<? extends View>> CTOR_CACHE =
            new ConcurrentHashMap<>();

    private final AppCompatDelegate delegate;
    private final LayoutInflater inflater;

    public I18nViewFactory(AppCompatDelegate delegate, LayoutInflater inflater) {
        this.delegate = delegate;
        this.inflater = inflater;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        // AppCompat erzeugt die Framework-Views (TextView → AppCompatTextView usw.).
        View view = delegate.createView(parent, name, context, attrs);
        // Eigene/Material-Views (voll qualifizierter Name) selbst erzeugen, damit auch deren Layout-Texte
        // übersetzt werden. Theming bleibt korrekt, weil der bereits themenbehaftete context (die
        // android:theme-Umhüllung passiert vor diesem Aufruf) an den (Context, AttributeSet)-Konstruktor
        // geht – genau wie es der LayoutInflater intern tut. Bei Fehlern null → Standard-Inflater übernimmt.
        if (view == null && name.indexOf('.') > -1) {
            view = createByReflection(name, context, attrs);
        }
        if (view != null) {
            translate(view, context, attrs);
        }
        return view;
    }

    private View createByReflection(String name, Context context, AttributeSet attrs) {
        try {
            Constructor<? extends View> ctor = CTOR_CACHE.get(name);
            if (ctor == null) {
                ctor = context.getClassLoader().loadClass(name).asSubclass(View.class)
                        .getConstructor(CTOR_SIG);
                CTOR_CACHE.put(name, ctor);
            }
            return ctor.newInstance(context, attrs);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    private void translate(View view, Context context, AttributeSet attrs) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String attr = attrs.getAttributeName(i);
            if (!isTextAttr(attr)) {
                continue;
            }
            int resId = attrs.getAttributeResourceValue(i, 0);
            if (resId == 0) {
                continue;
            }
            String translated;
            try {
                translated = Strings.get(context.getResources().getResourceEntryName(resId));
            } catch (Exception e) {
                continue;
            }
            if (translated != null) {
                apply(view, attr, translated);
            }
        }
    }

    private static boolean isTextAttr(String attr) {
        return "text".equals(attr) || "hint".equals(attr) || "title".equals(attr)
                || "subtitle".equals(attr) || "contentDescription".equals(attr);
    }

    private void apply(View view, String attr, String text) {
        switch (attr) {
            case "text":
                if (view instanceof TextView) {
                    ((TextView) view).setText(text);
                }
                break;
            case "hint":
                if (view instanceof TextInputLayout) {
                    ((TextInputLayout) view).setHint(text);
                } else if (view instanceof TextView) {
                    ((TextView) view).setHint(text);
                }
                break;
            case "title":
                if (view instanceof Toolbar) {
                    ((Toolbar) view).setTitle(text);
                }
                break;
            case "subtitle":
                if (view instanceof Toolbar) {
                    ((Toolbar) view).setSubtitle(text);
                }
                break;
            case "contentDescription":
                view.setContentDescription(text);
                break;
            default:
                break;
        }
    }
}
