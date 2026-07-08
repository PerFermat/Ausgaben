package de.spahr.ausgaben.wear;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Übersetzt beim Aufblasen die Text-Attribute der Uhr-Layouts (die {@code @string/…} verweisen) anhand von
 * {@link WearStrings}. Nötig, weil {@code @string/…} in Layouts die {@code Resources}-Überschreibung umgeht.
 */
public class WearViewFactory implements LayoutInflater.Factory2 {

    private final AppCompatDelegate delegate;
    private final LayoutInflater inflater;

    public WearViewFactory(AppCompatDelegate delegate, LayoutInflater inflater) {
        this.delegate = delegate;
        this.inflater = inflater;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        View view = delegate.createView(parent, name, context, attrs);
        // Eigene/Material-Views über den LayoutInflater selbst erzeugen (kein eigenes Reflection); der
        // themenbehaftete context sorgt für korrektes Styling. Bei Fehlern übernimmt der Standard-Inflater.
        if (view == null && name.indexOf('.') > -1) {
            try {
                view = Build.VERSION.SDK_INT >= 29
                        ? inflater.createView(context, name, null, attrs)
                        : inflater.createView(name, null, attrs);
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
