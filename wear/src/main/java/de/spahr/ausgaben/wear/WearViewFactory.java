package de.spahr.ausgaben.wear;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Übersetzt beim Aufblasen die Text-Attribute der Uhr-Layouts (die {@code @string/…} verweisen) anhand von
 * {@link WearStrings}. Nötig, weil {@code @string/…} in Layouts die {@code Resources}-Überschreibung umgeht.
 */
public class WearViewFactory implements LayoutInflater.Factory2 {

    private static final Class<?>[] CTOR_SIG = {Context.class, AttributeSet.class};
    private static final ConcurrentHashMap<String, Constructor<? extends View>> CTOR_CACHE =
            new ConcurrentHashMap<>();

    private final AppCompatDelegate delegate;
    private final LayoutInflater inflater;

    public WearViewFactory(AppCompatDelegate delegate, LayoutInflater inflater) {
        this.delegate = delegate;
        this.inflater = inflater;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        View view = delegate.createView(parent, name, context, attrs);
        // Eigene/Material-Views selbst über den themenbehafteten context erzeugen (korrektes Styling).
        if (view == null && name.indexOf('.') > -1) {
            try {
                Constructor<? extends View> ctor = CTOR_CACHE.get(name);
                if (ctor == null) {
                    ctor = context.getClassLoader().loadClass(name).asSubclass(View.class)
                            .getConstructor(CTOR_SIG);
                    CTOR_CACHE.put(name, ctor);
                }
                view = ctor.newInstance(context, attrs);
            } catch (Throwable ignored) {
            }
        }
        if (view instanceof TextView) {
            translate((TextView) view, context, attrs);
        }
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    private void translate(TextView view, Context context, AttributeSet attrs) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String attr = attrs.getAttributeName(i);
            if (!"text".equals(attr) && !"hint".equals(attr) && !"contentDescription".equals(attr)) {
                continue;
            }
            int resId = attrs.getAttributeResourceValue(i, 0);
            if (resId == 0) {
                continue;
            }
            String t;
            try {
                t = WearStrings.get(context.getResources().getResourceEntryName(resId));
            } catch (Exception e) {
                continue;
            }
            if (t == null) {
                continue;
            }
            if ("text".equals(attr)) {
                view.setText(t);
            } else if ("hint".equals(attr)) {
                view.setHint(t);
            } else {
                view.setContentDescription(t);
            }
        }
    }
}
